"""Browser workspace isolation for CVs; no claim of a full account system."""
import hashlib
import hmac
import json
import re
import secrets
import time

from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from .db_utils import get_db_connection
from .session_context import workspace_id
from .settings import COOKIE_NAME, COOKIE_SECURE, SESSION_SECONDS, ALLOWED_ORIGINS, MAX_PDF_BYTES

PUBLIC_GETS = {"/", "/health", "/jobs", "/jobs/analytics", "/docs", "/redoc", "/openapi.json"}
ID_ROUTES = re.compile(r"^/(?:cv/(\d+)/(?:insights)|applications/(\d+)|preview-doc(?:-info)?/(\d+))/?$")

def csrf_for(token):
    return hmac.new(token.encode(), b"yangcareer-csrf", hashlib.sha256).hexdigest()

def lookup_session(token):
    if not token or len(token) > 128:
        return None
    digest = hashlib.sha256(token.encode()).hexdigest()
    with get_db_connection() as conn:
        row = conn.execute(
            "SELECT workspace_id FROM workspace_sessions WHERE token_hash = ? AND expires_at > ?",
            (digest, int(time.time()))
        ).fetchone()
    return row["workspace_id"] if row else None

def cv_belongs_to(cv_id, owner):
    with get_db_connection() as conn:
        return conn.execute(
            "SELECT 1 FROM cv_store WHERE id = ? AND owner_id = ?", (cv_id, owner)
        ).fetchone() is not None

def private_error(status, detail):
    return JSONResponse({"detail": detail}, status_code=status, headers={"Cache-Control": "no-store"})

async def open_workspace(request):
    token = request.cookies.get(COOKIE_NAME)
    owner = lookup_session(token)
    created = owner is None
    if created:
        token = secrets.token_urlsafe(32)
        owner = secrets.token_hex(16)
        with get_db_connection() as conn:
            conn.execute(
                "INSERT INTO workspace_sessions(token_hash,workspace_id,expires_at) VALUES(?,?,?)",
                (hashlib.sha256(token.encode()).hexdigest(), owner, int(time.time()) + SESSION_SECONDS)
            )
            conn.execute("DELETE FROM workspace_sessions WHERE expires_at < ?", (int(time.time()),))
            conn.commit()
    response = JSONResponse({"csrf_token": csrf_for(token), "expires_in": SESSION_SECONDS}, headers={"Cache-Control": "no-store"})
    if created:
        response.set_cookie(
            COOKIE_NAME, token, max_age=SESSION_SECONDS, httponly=True,
            secure=COOKIE_SECURE, samesite="lax", path="/"
        )
    return response

class WorkspaceMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        path = request.url.path.rstrip("/") or "/"
        if request.method == "OPTIONS" or (path == "/app" or path.startswith("/app/")) or (
            request.method == "GET" and path in PUBLIC_GETS
        ):
            return await call_next(request)
        if path == "/workspace" and request.method == "GET":
            return await call_next(request)
        token = request.cookies.get(COOKIE_NAME)
        owner = lookup_session(token)
        if not owner:
            return private_error(401, "Open a workspace before accessing CV data.")
        if request.method not in ("GET", "HEAD"):
            origin = request.headers.get("origin")
            if origin and origin.rstrip("/") not in ALLOWED_ORIGINS:
                return private_error(403, "Origin is not allowed.")
            if not hmac.compare_digest(request.headers.get("X-CSRF-Token", ""), csrf_for(token)):
                return private_error(403, "Invalid CSRF token.")
            try:
                content_length = int(request.headers.get("content-length", "0"))
            except ValueError:
                return private_error(400, "Invalid Content-Length.")
            if content_length > MAX_PDF_BYTES + 65536:
                return private_error(413, "Request exceeds the upload limit.")
        ids = []
        match = ID_ROUTES.fullmatch(path)
        if match:
            ids.extend(int(value) for value in match.groups() if value)
        for name in ("cv_id", "file_id"):
            value = request.query_params.get(name)
            if value:
                try:
                    ids.append(int(value))
                except (ValueError, TypeError):
                    return private_error(422, "Invalid CV id.")
        if request.method not in ("GET", "HEAD") and "application/json" in request.headers.get("content-type", ""):
            body = await request.body()
            if len(body) > 65536:
                return private_error(413, "JSON request is too large.")
            try:
                payload = json.loads(body) if body else {}
                if isinstance(payload, dict):
                    ids.extend(int(payload[name]) for name in ("cv_id", "file_id") if payload.get(name) is not None)
            except (ValueError, TypeError):
                return private_error(422, "Invalid request body.")
        if any(value < 1 or not cv_belongs_to(value, owner) for value in ids):
            return private_error(404, "CV not found.")
        request.state.workspace_id = owner
        marker = workspace_id.set(owner)
        try:
            response = await call_next(request)
            response.headers["Cache-Control"] = "no-store"
            response.headers["X-Content-Type-Options"] = "nosniff"
            return response
        finally:
            workspace_id.reset(marker)
