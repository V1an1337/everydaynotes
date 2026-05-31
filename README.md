# EverydayNotes

EverydayNotes is a private Android + Web capture system for screenshots, Douyin shares, search, and manual random recall.

## Repository Layout

- `backend/` - FastAPI API, SQLAlchemy models, media storage, background worker, pytest coverage.
- `web/` - React + Vite companion app.
- `android/` - Kotlin + Jetpack Compose Android client.
- `deploy/` - Ubuntu `systemd` and Nginx templates for `notes.v1an.xyz`.

## Backend Quick Start

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev]"
$env:EVERYDAYNOTES_PASSWORD="change-me"
python -m everydaynotes.cli init-db
uvicorn everydaynotes.app:app --reload --port 8017
```

Run the worker in another terminal:

```powershell
cd backend
.\.venv\Scripts\Activate.ps1
python -m everydaynotes.worker
```

## Web Quick Start

```powershell
cd web
npm install
npm run dev
```

The web app expects the API at `/api` in production and can use `VITE_API_BASE=http://127.0.0.1:8017/api` in development.

## Server Notes

On Ubuntu 24, install the backend under `/opt/everydaynotes/backend`, serve the web build from `/var/www/everydaynotes`, configure `/etc/everydaynotes/everydaynotes.env`, then install the service templates from `deploy/`.

Generate a production password hash with:

```bash
python -m everydaynotes.cli hash-password "your-long-password"
```

Initialize PostgreSQL tables once:

```bash
EVERYDAYNOTES_AUTO_CREATE_TABLES=true python -m everydaynotes.cli init-db
```

