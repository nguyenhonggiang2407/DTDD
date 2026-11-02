import os
import secrets
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request, UploadFile
from fastapi.concurrency import run_in_threadpool
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse, Response
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, ConfigDict, Field, field_validator
from starlette.middleware.trustedhost import TrustedHostMiddleware

from app.limits import UploadLimitMiddleware
from app.store import SESSION_DAYS, Store
from app.workflow import MAX_BYTES, DocumentError, DocumentWorkflow

ROOT = Path(__file__).resolve().parent
COOKIE = "yangdocs_workspace"


class SearchInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    query: str = Field(min_length=2, max_length=200)
    favorites: bool = False

    @field_validator("query")
    @classmethod
    def trim_query(cls, value: str):
        value = value.strip()
        if len(value) < 2:
            raise ValueError("Query is empty")
        return value


class FavoriteInput(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    favorite: bool


def create_app(database: Path | None = None) -> FastAPI:
    app = FastAPI(title="YangDocs", version="1.0.0", docs_url=None, redoc_url=None)
    store = Store(database or Path(os.getenv("YANGDOCS_DB", "data/yangdocs.sqlite3")))
    workflow = DocumentWorkflow(store)
    app.state.store, app.state.workflow = store, workflow
    app.add_middleware(UploadLimitMiddleware)
    app.add_middleware(
        TrustedHostMiddleware,
        allowed_hosts=os.getenv("YANGDOCS_ALLOWED_HOSTS", "localhost,127.0.0.1,testserver").split(
            ","
        ),
    )

    @app.middleware("http")
    async def protect(request: Request, call_next):
        response = None
        if request.url.path.startswith("/api/"):
            session = store.session(request.cookies.get(COOKIE))
            request.state.session = session
            if request.url.path != "/api/session" and not session:
                response = JSONResponse(
                    {"detail": "Workspace đã hết hạn. Hãy tải lại trang."}, status_code=401
                )
            if request.method not in ("GET", "HEAD", "OPTIONS"):
                origin = request.headers.get("origin")
                expected_origin = str(request.base_url).rstrip("/")
                if origin and origin != expected_origin:
                    response = JSONResponse({"detail": "Origin không hợp lệ."}, status_code=403)
                elif not session or not secrets.compare_digest(
                    request.headers.get("x-csrf-token", ""), session["csrf"]
                ):
                    response = JSONResponse(
                        {"detail": "Phiên xác thực không hợp lệ."}, status_code=403
                    )
        if response is None:
            response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "same-origin"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            "connect-src 'self'; font-src 'self'; object-src 'none'; base-uri 'none'; "
            "frame-ancestors 'none'; form-action 'self'"
        )
        if request.url.path.startswith("/api/"):
            response.headers["Cache-Control"] = "no-store"
            response.headers["Vary"] = "Cookie"
        return response

    @app.exception_handler(DocumentError)
    async def document_error(request: Request, exc: DocumentError):
        return JSONResponse({"detail": exc.message}, status_code=exc.status)

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, exc: RequestValidationError):
        # Do not reflect uploaded contents, query text or headers into validation errors.
        return JSONResponse(
            {"detail": "Dữ liệu không hợp lệ. Từ khóa cần từ 2 đến 200 ký tự."}, status_code=422
        )

    @app.get("/")
    def home():
        return FileResponse(ROOT / "static/index.html")

    @app.get("/health")
    def health():
        return {"status": "ok"}

    @app.get("/api/session")
    def session(request: Request, response: Response):
        session = request.state.session
        if session is None:
            cookie, session = store.new_session()
            response.set_cookie(
                COOKIE,
                cookie,
                max_age=SESSION_DAYS * 86400,
                httponly=True,
                samesite="strict",
                secure=os.getenv("YANGDOCS_SECURE_COOKIE", "0") == "1",
                path="/",
            )
        return {"csrf": session["csrf"], "retention_days": SESSION_DAYS}

    @app.get("/api/documents")
    def documents(request: Request):
        docs = store.documents(request.state.session["id"])
        return {
            "documents": docs,
            "stats": {
                "documents": len(docs),
                "pages": sum(d["page_count"] for d in docs),
                "favorites": sum(d["favorite"] for d in docs),
            },
        }

    @app.post("/api/documents", status_code=201)
    async def upload(request: Request, file: UploadFile):
        try:
            # Read a bounded amount from the parser's spooled upload, never its supplied path.
            data = await file.read(MAX_BYTES + 1)
            if len(data) > MAX_BYTES:
                raise DocumentError("Mỗi tài liệu tối đa 5 MB.", 413)
            return await run_in_threadpool(
                workflow.ingest, request.state.session["id"], file.filename or "", data
            )
        finally:
            await file.close()

    @app.post("/api/demo")
    def demo(request: Request):
        added = []
        for path in sorted((ROOT.parent / "examples").glob("*.md")):
            try:
                added.append(
                    workflow.ingest(request.state.session["id"], path.name, path.read_bytes())
                )
            except DocumentError as exc:
                if exc.status != 409:
                    raise
        return {"added": len(added)}

    @app.patch("/api/documents/{document_id}")
    def favorite(document_id: str, payload: FavoriteInput, request: Request):
        if not store.favorite(request.state.session["id"], document_id, payload.favorite):
            raise HTTPException(404, "Không tìm thấy tài liệu.")
        return {"favorite": payload.favorite}

    @app.delete("/api/documents/{document_id}", status_code=204)
    def delete(document_id: str, request: Request):
        if not store.delete(request.state.session["id"], document_id):
            raise HTTPException(404, "Không tìm thấy tài liệu.")
        return Response(status_code=204)

    @app.get("/api/documents/{document_id}/pages/{number}")
    def page(document_id: str, number: int, request: Request):
        owner = request.state.session["id"]
        doc = store.document(owner, document_id)
        text = store.page(owner, document_id, number)
        if doc is None or text is None:
            raise HTTPException(404, "Không tìm thấy trang tài liệu.")
        return {"document": doc, "number": number, "text": text}

    @app.post("/api/search")
    def search(payload: SearchInput, request: Request):
        return workflow.present(request.state.session["id"], payload.query, payload.favorites)

    @app.post("/api/export")
    def export(payload: SearchInput, request: Request):
        result = workflow.present(request.state.session["id"], payload.query, payload.favorites)
        if not result["hits"]:
            raise HTTPException(404, "Chưa có kết quả để xuất.")
        lines = [
            "# YangDocs · Ghi chú có nguồn",
            "",
            "Từ khóa: " + result["query"],
            "",
            "Các đoạn trích nguyên văn từ tài liệu. Đây không phải câu trả lời do AI tạo.",
            "",
        ]
        for hit in result["hits"]:
            name = hit["name"].replace("`", "'")
            lines += [f"## [{hit['citation']}] `{name}` · Trang {hit['page']}", ""]
            lines += ["> " + line for line in hit["text"].splitlines()]
            lines += [""]
        return Response(
            "\n".join(lines),
            media_type="text/markdown; charset=utf-8",
            headers={"Content-Disposition": 'attachment; filename="yangdocs-notes.md"'},
        )

    app.mount("/static", StaticFiles(directory=ROOT / "static"), name="static")
    return app


app = create_app()
