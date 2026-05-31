from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .api import router
from .db import create_db_engine, create_session_factory, init_db
from .settings import Settings


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    app = FastAPI(title="EverydayNotes API", version="0.1.0")
    app.state.settings = settings
    app.state.engine = create_db_engine(settings)
    app.state.SessionLocal = create_session_factory(app.state.engine)
    if settings.auto_create_tables:
        init_db(app.state.engine)

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(router)
    return app


app = create_app()

