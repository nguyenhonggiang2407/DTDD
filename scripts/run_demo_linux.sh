#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r backend/requirements.txt
export COURTMATE_ADMIN_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(48))')"
export COURTMATE_DB="$PWD/backend/courtmate_demo.db"
export COURTMATE_PUBLIC_BOOKING=true
python scripts/seed_demo.py
echo "Open http://127.0.0.1:8000/"
cd backend
exec python -m uvicorn app:app --host 127.0.0.1 --port 8000
