from __future__ import annotations

from pathlib import Path

from fastapi.testclient import TestClient

from everydaynotes.settings import Settings
from everydaynotes.storage import MediaStorage
from everydaynotes.worker import process_once


def test_ocr_worker_updates_search_text(
    client: TestClient, auth_headers: dict[str, str], test_settings: Settings, monkeypatch
) -> None:
    created = client.post(
        "/api/captures/screenshot",
        headers=auth_headers,
        files={"file": ("shot.png", b"image", "image/png")},
    ).json()
    monkeypatch.setattr("everydaynotes.worker.extract_text_from_image", lambda path: "orange notebook")

    assert process_once(test_settings) is True

    search = client.get("/api/notes", headers=auth_headers, params={"query": "orange"})
    assert search.status_code == 200
    assert search.json()["items"][0]["id"] == created["id"]
    assert search.json()["items"][0]["status"] == "ready"


def test_douyin_worker_fills_metadata_and_downloads_assets(
    client: TestClient, auth_headers: dict[str, str], test_settings: Settings, monkeypatch
) -> None:
    created = client.post(
        "/api/captures/douyin",
        headers=auth_headers,
        json={"share_text": "4.89 https://v.douyin.com/oSzS4bbF--I/ :/"},
    ).json()

    monkeypatch.setattr(
        "everydaynotes.worker.fetch_douyin_metadata",
        lambda settings, url: {
            "code": "200",
            "title": "回国创业后，生活水平是下降的",
            "author": "敦兰",
            "url": "https://cdn.invalid/video.mp4",
            "cover": "https://cdn.invalid/cover.jpg",
        },
    )

    def fake_download(self: MediaStorage, note_id: str, kind: str, url: str, timeout: float = 45.0):
        mime = "video/mp4" if kind == "video" else "image/jpeg"
        return self.save_bytes(note_id, kind, f"{kind}.bin", f"{kind}:{url}".encode(), mime, original_url=url)

    monkeypatch.setattr(MediaStorage, "download_url", fake_download)

    assert process_once(test_settings) is True

    note = client.get(f"/api/notes/{created['id']}", headers=auth_headers)
    assert note.status_code == 200
    payload = note.json()
    assert payload["title"] == "回国创业后，生活水平是下降的"
    assert payload["author"] == "敦兰"
    assert {asset["kind"] for asset in payload["assets"]} == {"video", "cover"}

