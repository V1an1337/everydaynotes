from __future__ import annotations

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from everydaynotes.app import create_app
from everydaynotes.settings import Settings


@pytest.fixture()
def test_settings(tmp_path: Path) -> Settings:
    db_path = tmp_path / "everydaynotes-test.db"
    return Settings(
        database_url=f"sqlite:///{db_path.as_posix()}",
        media_root=tmp_path / "media",
        auth_password="test-pass",
        auth_password_hash=None,
        token_ttl_days=30,
        douyin_parser_url="https://parser.invalid/",
        cors_origins=["http://localhost:5173"],
        auto_create_tables=True,
    )


@pytest.fixture()
def client(test_settings: Settings) -> TestClient:
    return TestClient(create_app(test_settings))


@pytest.fixture()
def token(client: TestClient) -> str:
    response = client.post("/api/auth/login", json={"password": "test-pass", "device_name": "pytest"})
    assert response.status_code == 200
    return response.json()["access_token"]


@pytest.fixture()
def auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}

