from __future__ import annotations

from typing import Any

import httpx

from .settings import Settings


class DouyinParseError(RuntimeError):
    pass


def fetch_douyin_metadata(settings: Settings, url: str) -> dict[str, Any]:
    with httpx.Client(follow_redirects=True, timeout=20.0) as client:
        response = client.get(settings.douyin_parser_url, params={"url": url})
        response.raise_for_status()
        data = response.json()
    code = str(data.get("code", ""))
    if code not in {"200", "0"}:
        raise DouyinParseError(data.get("msg") or "Douyin parser returned an error")
    return data


def preferred_video_url(data: dict[str, Any]) -> str | None:
    value = data.get("url") or data.get("video_url")
    return value if isinstance(value, str) and value.startswith("http") else None


def preferred_cover_url(data: dict[str, Any]) -> str | None:
    for key in ("cover", "cover_url", "dynamic_cover"):
        value = data.get(key)
        if isinstance(value, str) and value.startswith("http"):
            return value
    return None


def title_from_metadata(data: dict[str, Any]) -> str | None:
    value = data.get("title") or data.get("desc")
    return str(value).strip() if value else None


def author_from_metadata(data: dict[str, Any]) -> str | None:
    value = data.get("author") or data.get("nickname")
    return str(value).strip() if value else None

