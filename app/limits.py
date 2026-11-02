"""Bound multipart request bodies before Starlette spools uploaded files."""

from starlette.exceptions import HTTPException
from starlette.responses import JSONResponse

from app.workflow import MAX_BYTES


class UploadLimitMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if (
            scope["type"] != "http"
            or scope["path"] != "/api/documents"
            or scope["method"] != "POST"
        ):
            return await self.app(scope, receive, send)
        limit = MAX_BYTES + 64 * 1024  # multipart headers and boundary
        headers = dict(scope["headers"])
        try:
            length = int(headers.get(b"content-length", b"0"))
        except ValueError:
            length = limit + 1
        if length > limit:
            return await JSONResponse({"detail": "Mỗi tài liệu tối đa 5 MB."}, status_code=413)(
                scope, receive, send
            )
        received = 0

        async def bounded_receive():
            nonlocal received
            message = await receive()
            received += len(message.get("body", b""))
            if received > limit:
                raise HTTPException(413, "Mỗi tài liệu tối đa 5 MB.")
            return message

        await self.app(scope, bounded_receive, send)
