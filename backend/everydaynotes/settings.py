from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _csv(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


@dataclass(frozen=True)
class Settings:
    database_url: str
    media_root: Path
    auth_password: str
    auth_password_hash: str | None
    token_ttl_days: int
    douyin_parser_url: str
    cors_origins: list[str]
    auto_create_tables: bool

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            database_url=os.getenv("EVERYDAYNOTES_DATABASE_URL", "sqlite:///./everydaynotes-dev.db"),
            media_root=Path(os.getenv("EVERYDAYNOTES_MEDIA_ROOT", "./var/media")).resolve(),
            auth_password=os.getenv("EVERYDAYNOTES_PASSWORD", "change-me"),
            auth_password_hash=os.getenv("EVERYDAYNOTES_PASSWORD_HASH"),
            token_ttl_days=int(os.getenv("EVERYDAYNOTES_TOKEN_TTL_DAYS", "365")),
            douyin_parser_url=os.getenv("EVERYDAYNOTES_DOUYIN_API", "https://api.bbbzd.cn/"),
            cors_origins=_csv(os.getenv("EVERYDAYNOTES_CORS_ORIGINS", "http://localhost:5173")),
            auto_create_tables=os.getenv("EVERYDAYNOTES_AUTO_CREATE_TABLES", "true").lower()
            in {"1", "true", "yes", "on"},
        )

