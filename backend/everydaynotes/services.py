from __future__ import annotations

import re
from datetime import datetime

from sqlalchemy import or_, select
from sqlalchemy.orm import Session, selectinload

from .models import Asset, Job, Note, Tag


URL_RE = re.compile(r"https?://[^\s，。；;:'\"<>]+", re.IGNORECASE)


def extract_first_url(text: str) -> str | None:
    match = URL_RE.search(text or "")
    if not match:
        return None
    return match.group(0).rstrip(":/")


def normalize_tags(tags: list[str] | str | None) -> list[str]:
    if tags is None:
        return []
    if isinstance(tags, str):
        raw = re.split(r"[,，#\s]+", tags)
    else:
        raw = tags
    seen: set[str] = set()
    result: list[str] = []
    for item in raw:
        name = item.strip().lower()
        if name and name not in seen:
            seen.add(name)
            result.append(name[:80])
    return result


def set_note_tags(db: Session, note: Note, tags: list[str] | str | None) -> None:
    note.tags.clear()
    for name in normalize_tags(tags):
        tag = db.scalar(select(Tag).where(Tag.name == name))
        if tag is None:
            tag = Tag(name=name)
            db.add(tag)
            db.flush()
        note.tags.append(tag)


def create_job(db: Session, note: Note, job_type: str, payload: dict | None = None) -> Job:
    job = Job(type=job_type, note=note, payload=payload or {})
    db.add(job)
    return job


def asset_to_dict(asset: Asset) -> dict:
    return {
        "id": asset.id,
        "kind": asset.kind,
        "mime_type": asset.mime_type,
        "file_name": asset.file_name,
        "size_bytes": asset.size_bytes,
        "sha256": asset.sha256,
        "url": f"/api/assets/{asset.id}",
        "created_at": asset.created_at.isoformat(),
    }


def note_to_dict(note: Note) -> dict:
    return {
        "id": note.id,
        "type": note.type,
        "title": note.title,
        "source_url": note.source_url,
        "source_text": note.source_text,
        "author": note.author,
        "remark": note.remark,
        "ocr_text": note.ocr_text,
        "status": note.status,
        "created_at": note.created_at.isoformat(),
        "updated_at": note.updated_at.isoformat(),
        "tags": sorted(tag.name for tag in note.tags),
        "assets": [asset_to_dict(asset) for asset in note.assets],
    }


def query_notes(
    db: Session,
    query: str | None = None,
    note_type: str | None = None,
    tag: str | None = None,
    from_date: datetime | None = None,
    to_date: datetime | None = None,
    limit: int = 80,
) -> list[Note]:
    stmt = select(Note).options(selectinload(Note.assets), selectinload(Note.tags))
    if note_type:
        stmt = stmt.where(Note.type == note_type)
    if from_date:
        stmt = stmt.where(Note.created_at >= from_date)
    if to_date:
        stmt = stmt.where(Note.created_at <= to_date)
    if query:
        pattern = f"%{query.strip()}%"
        stmt = stmt.where(
            or_(
                Note.title.ilike(pattern),
                Note.author.ilike(pattern),
                Note.source_url.ilike(pattern),
                Note.source_text.ilike(pattern),
                Note.remark.ilike(pattern),
                Note.ocr_text.ilike(pattern),
            )
        )
    if tag:
        stmt = stmt.join(Note.tags).where(Tag.name == tag.strip().lower())
    stmt = stmt.order_by(Note.created_at.desc()).limit(min(limit, 200))
    return list(db.scalars(stmt).unique())
