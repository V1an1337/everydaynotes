from __future__ import annotations

import hashlib
import mimetypes
import re
from dataclasses import dataclass
from pathlib import Path

import httpx
from fastapi import UploadFile

from .settings import Settings


_SAFE_NAME = re.compile(r"[^A-Za-z0-9._-]+")


@dataclass(frozen=True)
class StoredFile:
    file_name: str
    file_path: str
    mime_type: str | None
    size_bytes: int
    sha256: str
    original_url: str | None = None


def safe_filename(name: str, fallback_ext: str = ".bin") -> str:
    cleaned = _SAFE_NAME.sub("-", Path(name).name).strip(".-")
    if not cleaned:
        cleaned = f"asset{fallback_ext}"
    if "." not in cleaned and fallback_ext:
        cleaned += fallback_ext
    return cleaned[:180]


class MediaStorage:
    def __init__(self, settings: Settings):
        self.root = settings.media_root
        self.root.mkdir(parents=True, exist_ok=True)

    def note_dir(self, note_id: str) -> Path:
        path = self.root / note_id
        path.mkdir(parents=True, exist_ok=True)
        return path

    def abs_path(self, relative_path: str) -> Path:
        path = (self.root / relative_path).resolve()
        if self.root not in path.parents and path != self.root:
            raise ValueError("asset path escapes media root")
        return path

    async def save_upload(self, note_id: str, kind: str, upload: UploadFile) -> StoredFile:
        content = await upload.read()
        original = upload.filename or f"{kind}.bin"
        return self.save_bytes(note_id, kind, original, content, upload.content_type)

    def save_bytes(
        self,
        note_id: str,
        kind: str,
        original_name: str,
        content: bytes,
        mime_type: str | None = None,
        original_url: str | None = None,
    ) -> StoredFile:
        ext = Path(original_name).suffix or mimetypes.guess_extension(mime_type or "") or ".bin"
        digest = hashlib.sha256(content).hexdigest()
        file_name = safe_filename(f"{kind}-{digest[:12]}{ext}", ext)
        absolute = self.note_dir(note_id) / file_name
        absolute.write_bytes(content)
        relative = absolute.relative_to(self.root).as_posix()
        return StoredFile(
            file_name=file_name,
            file_path=relative,
            mime_type=mime_type or mimetypes.guess_type(file_name)[0],
            size_bytes=len(content),
            sha256=digest,
            original_url=original_url,
        )

    def download_url(self, note_id: str, kind: str, url: str, timeout: float = 45.0) -> StoredFile:
        with httpx.Client(follow_redirects=True, timeout=timeout) as client:
            response = client.get(url)
            response.raise_for_status()
            content_type = response.headers.get("content-type", "").split(";")[0] or None
            suffix = mimetypes.guess_extension(content_type or "") or Path(httpx.URL(url).path).suffix or ".bin"
            return self.save_bytes(note_id, kind, f"{kind}{suffix}", response.content, content_type, original_url=url)

