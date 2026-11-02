"""Configuration for the YangCareer derivative."""
import os
from pathlib import Path
from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
load_dotenv(ROOT / ".env")
DB_PATH = Path(os.environ.get("YANGCAREER_DB", str(ROOT / "db" / "yangcareer.sqlite3"))).resolve()
DB_PATH.parent.mkdir(parents=True, exist_ok=True)
MAX_PDF_BYTES = 10 * 1024 * 1024
SESSION_SECONDS = 14 * 24 * 60 * 60
COOKIE_NAME = "yangcareer_workspace"
COOKIE_SECURE = os.environ.get("COOKIE_SECURE", "0") == "1"
ALLOWED_ORIGINS = tuple(
    value.strip().rstrip("/") for value in os.environ.get(
        "ALLOWED_ORIGINS",
        "http://127.0.0.1:9990,http://localhost:9990,http://127.0.0.1:8000,http://localhost:8000"
    ).split(",") if value.strip()
)
if "*" in ALLOWED_ORIGINS:
    raise ValueError("ALLOWED_ORIGINS must contain explicit origins.")
