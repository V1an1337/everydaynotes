from __future__ import annotations

import argparse
import time
from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from .db import create_db_engine, create_session_factory
from .douyin import author_from_metadata, fetch_douyin_metadata, preferred_cover_url, preferred_video_url, title_from_metadata
from .models import Asset, Job, Note, now_utc
from .ocr import extract_text_from_image
from .settings import Settings
from .storage import MediaStorage


def claim_next_job(db: Session) -> Job | None:
    job = db.scalar(
        select(Job)
        .where(Job.status.in_(["queued", "failed"]), Job.attempts < 3)
        .order_by(Job.created_at.asc())
        .limit(1)
    )
    if job is None:
        return None
    job.status = "running"
    job.updated_at = now_utc()
    db.commit()
    db.refresh(job)
    return job


def add_asset_from_stored(db: Session, note: Note, kind: str, stored) -> Asset:
    asset = Asset(
        note=note,
        kind=kind,
        mime_type=stored.mime_type,
        file_name=stored.file_name,
        file_path=stored.file_path,
        original_url=stored.original_url,
        size_bytes=stored.size_bytes,
        sha256=stored.sha256,
    )
    db.add(asset)
    return asset


def process_douyin_job(db: Session, settings: Settings, storage: MediaStorage, job: Job) -> None:
    note = db.scalar(
        select(Note).where(Note.id == job.note_id).options(selectinload(Note.assets), selectinload(Note.tags))
    )
    if note is None:
        return
    url = job.payload.get("url") or note.source_url
    if not url:
        raise RuntimeError("Douyin job has no URL")
    metadata = fetch_douyin_metadata(settings, url)
    note.title = title_from_metadata(metadata) or note.title
    note.author = author_from_metadata(metadata) or note.author
    note.status = "ready"

    video_url = preferred_video_url(metadata)
    if video_url:
        add_asset_from_stored(db, note, "video", storage.download_url(note.id, "video", video_url))

    cover_url = preferred_cover_url(metadata)
    if cover_url:
        add_asset_from_stored(db, note, "cover", storage.download_url(note.id, "cover", cover_url))


def process_ocr_job(db: Session, settings: Settings, storage: MediaStorage, job: Job) -> None:
    note = db.scalar(
        select(Note).where(Note.id == job.note_id).options(selectinload(Note.assets), selectinload(Note.tags))
    )
    if note is None:
        return
    screenshot = next((asset for asset in note.assets if asset.kind == "screenshot"), None)
    if screenshot is None:
        raise RuntimeError("OCR job has no screenshot asset")
    note.ocr_text = extract_text_from_image(storage.abs_path(screenshot.file_path))
    note.status = "ready"


def process_job(db: Session, settings: Settings, storage: MediaStorage, job: Job) -> None:
    if job.type == "douyin_download":
        process_douyin_job(db, settings, storage, job)
    elif job.type == "ocr":
        process_ocr_job(db, settings, storage, job)
    else:
        raise RuntimeError(f"Unknown job type: {job.type}")


def process_once(settings: Settings | None = None) -> bool:
    settings = settings or Settings.from_env()
    engine = create_db_engine(settings)
    SessionLocal = create_session_factory(engine)
    storage = MediaStorage(settings)
    with SessionLocal() as db:
        job = claim_next_job(db)
        if job is None:
            return False
        try:
            process_job(db, settings, storage, job)
            job.status = "completed"
            job.last_error = None
        except Exception as exc:
            job.status = "failed"
            job.attempts += 1
            job.last_error = str(exc)
        finally:
            job.updated_at = datetime.now(timezone.utc)
            db.commit()
        return True


def run_loop(settings: Settings, interval: float) -> None:
    while True:
        processed = process_once(settings)
        if not processed:
            time.sleep(interval)


def main() -> None:
    parser = argparse.ArgumentParser(prog="everydaynotes-worker")
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--interval", type=float, default=5.0)
    args = parser.parse_args()
    settings = Settings.from_env()
    if args.once:
        process_once(settings)
    else:
        run_loop(settings, args.interval)


if __name__ == "__main__":
    main()

