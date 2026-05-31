from __future__ import annotations

import random
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from .models import Asset, DeviceSession, Note, now_utc
from .security import expires_in_days, issue_token, password_matches, token_hash
from .services import create_job, extract_first_url, note_to_dict, query_notes, set_note_tags
from .storage import MediaStorage

router = APIRouter(prefix="/api")


class LoginIn(BaseModel):
    password: str
    device_name: str | None = None


class LoginOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_at: datetime


class DouyinCaptureIn(BaseModel):
    share_text: str = ""
    url: str | None = None
    remark: str | None = None
    tags: list[str] = Field(default_factory=list)


class NoteUpdateIn(BaseModel):
    title: str | None = None
    remark: str | None = None
    tags: list[str] | None = None


def get_db(request: Request):
    with request.app.state.SessionLocal() as db:
        yield db


def get_storage(request: Request) -> MediaStorage:
    return MediaStorage(request.app.state.settings)


def bearer_token(request: Request) -> str | None:
    auth = request.headers.get("authorization", "")
    if auth.lower().startswith("bearer "):
        return auth.split(" ", 1)[1].strip()
    query_token = request.query_params.get("token")
    return query_token.strip() if query_token else None


def require_session(request: Request, db: Session = Depends(get_db)) -> DeviceSession:
    token = bearer_token(request)
    if not token:
        raise HTTPException(status_code=401, detail="Missing bearer token")
    session = db.scalar(select(DeviceSession).where(DeviceSession.token_hash == token_hash(token)))
    if session is None or session.revoked_at is not None:
        raise HTTPException(status_code=401, detail="Invalid token")
    now = datetime.now(timezone.utc)
    expires_at = session.expires_at
    if expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if expires_at <= now:
        raise HTTPException(status_code=401, detail="Token expired")
    session.last_seen_at = now_utc()
    db.commit()
    return session


@router.get("/health")
def health() -> dict:
    return {"ok": True, "service": "everydaynotes"}


@router.post("/auth/login", response_model=LoginOut)
def login(payload: LoginIn, request: Request, db: Session = Depends(get_db)) -> LoginOut:
    settings = request.app.state.settings
    if not password_matches(payload.password, settings.auth_password, settings.auth_password_hash):
        raise HTTPException(status_code=401, detail="Wrong password")
    token = issue_token()
    expires_at = expires_in_days(settings.token_ttl_days)
    db.add(
        DeviceSession(
            device_name=payload.device_name,
            token_hash=token_hash(token),
            expires_at=expires_at,
            last_seen_at=now_utc(),
        )
    )
    db.commit()
    return LoginOut(access_token=token, expires_at=expires_at)


@router.get("/auth/me")
def me(session: DeviceSession = Depends(require_session)) -> dict:
    return {"device_name": session.device_name, "expires_at": session.expires_at.isoformat()}


@router.post("/captures/screenshot")
async def capture_screenshot(
    request: Request,
    file: UploadFile = File(...),
    remark: str | None = Form(default=None),
    tags: str | None = Form(default=None),
    db: Session = Depends(get_db),
    _: DeviceSession = Depends(require_session),
) -> dict:
    storage = MediaStorage(request.app.state.settings)
    note = Note(
        type="screenshot",
        title=f"Screenshot {datetime.now().strftime('%Y-%m-%d %H:%M')}",
        remark=remark.strip() if remark else None,
        status="processing",
    )
    db.add(note)
    db.flush()
    saved = await storage.save_upload(note.id, "screenshot", file)
    db.add(
        Asset(
            note=note,
            kind="screenshot",
            mime_type=saved.mime_type,
            file_name=saved.file_name,
            file_path=saved.file_path,
            size_bytes=saved.size_bytes,
            sha256=saved.sha256,
        )
    )
    set_note_tags(db, note, tags)
    create_job(db, note, "ocr", {"asset_kind": "screenshot"})
    db.commit()
    db.refresh(note)
    return note_to_dict(note)


@router.post("/captures/douyin")
def capture_douyin(
    payload: DouyinCaptureIn,
    db: Session = Depends(get_db),
    _: DeviceSession = Depends(require_session),
) -> dict:
    url = payload.url or extract_first_url(payload.share_text)
    if not url:
        raise HTTPException(status_code=400, detail="No URL found in Douyin share text")
    note = Note(
        type="douyin",
        title="Douyin capture",
        source_url=url,
        source_text=payload.share_text,
        remark=payload.remark.strip() if payload.remark else None,
        status="processing",
    )
    db.add(note)
    db.flush()
    set_note_tags(db, note, payload.tags)
    create_job(db, note, "douyin_download", {"url": url})
    db.commit()
    db.refresh(note)
    return note_to_dict(note)


@router.get("/notes")
def list_notes(
    query: str | None = None,
    type: str | None = Query(default=None),
    tag: str | None = None,
    from_date: datetime | None = Query(default=None, alias="from"),
    to_date: datetime | None = Query(default=None, alias="to"),
    limit: int = Query(default=80, ge=1, le=200),
    db: Session = Depends(get_db),
    _: DeviceSession = Depends(require_session),
) -> dict:
    notes = query_notes(db, query=query, note_type=type, tag=tag, from_date=from_date, to_date=to_date, limit=limit)
    return {"items": [note_to_dict(note) for note in notes]}


@router.get("/notes/random")
def random_note(db: Session = Depends(get_db), _: DeviceSession = Depends(require_session)) -> dict:
    notes = list(db.scalars(select(Note).options(selectinload(Note.assets), selectinload(Note.tags))))
    if not notes:
        raise HTTPException(status_code=404, detail="No notes yet")
    return note_to_dict(random.choice(notes))


@router.get("/notes/{note_id}")
def get_note(note_id: str, db: Session = Depends(get_db), _: DeviceSession = Depends(require_session)) -> dict:
    note = db.scalar(
        select(Note).where(Note.id == note_id).options(selectinload(Note.assets), selectinload(Note.tags))
    )
    if note is None:
        raise HTTPException(status_code=404, detail="Note not found")
    return note_to_dict(note)


@router.patch("/notes/{note_id}")
def update_note(
    note_id: str,
    payload: NoteUpdateIn,
    db: Session = Depends(get_db),
    _: DeviceSession = Depends(require_session),
) -> dict:
    note = db.scalar(
        select(Note).where(Note.id == note_id).options(selectinload(Note.assets), selectinload(Note.tags))
    )
    if note is None:
        raise HTTPException(status_code=404, detail="Note not found")
    if payload.title is not None:
        note.title = payload.title.strip() or None
    if payload.remark is not None:
        note.remark = payload.remark.strip() or None
    if payload.tags is not None:
        set_note_tags(db, note, payload.tags)
    db.commit()
    db.refresh(note)
    return note_to_dict(note)


@router.delete("/notes/{note_id}")
def delete_note(
    note_id: str,
    request: Request,
    db: Session = Depends(get_db),
    _: DeviceSession = Depends(require_session),
) -> dict:
    storage = MediaStorage(request.app.state.settings)
    note = db.scalar(select(Note).where(Note.id == note_id).options(selectinload(Note.assets)))
    if note is None:
        raise HTTPException(status_code=404, detail="Note not found")
    for asset in list(note.assets):
        path = storage.abs_path(asset.file_path)
        if path.exists():
            path.unlink()
    db.delete(note)
    db.commit()
    return {"deleted": True}


@router.get("/assets/{asset_id}")
def get_asset(
    asset_id: str,
    request: Request,
    db: Session = Depends(get_db),
    _: DeviceSession = Depends(require_session),
) -> FileResponse:
    asset = db.get(Asset, asset_id)
    if asset is None:
        raise HTTPException(status_code=404, detail="Asset not found")
    path = MediaStorage(request.app.state.settings).abs_path(asset.file_path)
    if not path.exists():
        raise HTTPException(status_code=404, detail="Asset file missing")
    return FileResponse(path, media_type=asset.mime_type, filename=asset.file_name)

