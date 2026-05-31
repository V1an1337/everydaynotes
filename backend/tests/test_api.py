from __future__ import annotations

from fastapi.testclient import TestClient


def test_login_rejects_wrong_password(client: TestClient) -> None:
    response = client.post("/api/auth/login", json={"password": "wrong"})
    assert response.status_code == 401


def test_screenshot_upload_search_and_asset(client: TestClient, auth_headers: dict[str, str], token: str) -> None:
    response = client.post(
        "/api/captures/screenshot",
        headers=auth_headers,
        data={"remark": "a tiny desk memory", "tags": "desk, art"},
        files={"file": ("shot.png", b"fake image bytes", "image/png")},
    )
    assert response.status_code == 200
    note = response.json()
    assert note["type"] == "screenshot"
    assert note["tags"] == ["art", "desk"]
    assert note["assets"][0]["kind"] == "screenshot"

    search = client.get("/api/notes", headers=auth_headers, params={"query": "desk"})
    assert search.status_code == 200
    assert search.json()["items"][0]["id"] == note["id"]

    asset_url = note["assets"][0]["url"]
    asset_response = client.get(f"{asset_url}?token={token}")
    assert asset_response.status_code == 200
    assert asset_response.content == b"fake image bytes"


def test_update_note_tags_and_random(client: TestClient, auth_headers: dict[str, str]) -> None:
    created = client.post(
        "/api/captures/douyin",
        headers=auth_headers,
        json={"share_text": "看看这个 https://v.douyin.com/abc123/ :/", "tags": ["old"]},
    ).json()
    updated = client.patch(
        f"/api/notes/{created['id']}",
        headers=auth_headers,
        json={"remark": "changed", "tags": ["new", "memory"]},
    )
    assert updated.status_code == 200
    assert updated.json()["remark"] == "changed"
    assert updated.json()["tags"] == ["memory", "new"]

    random_note = client.get("/api/notes/random", headers=auth_headers)
    assert random_note.status_code == 200
    assert random_note.json()["id"] == created["id"]


def test_douyin_requires_url(client: TestClient, auth_headers: dict[str, str]) -> None:
    response = client.post("/api/captures/douyin", headers=auth_headers, json={"share_text": "no link here"})
    assert response.status_code == 400
