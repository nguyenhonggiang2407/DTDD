"""Bounded PDF reads and server-generated temporary paths."""
import os
import re
import tempfile
from fastapi import HTTPException
from .settings import MAX_PDF_BYTES

async def read_pdf(upload):
    filename = (upload.filename or "").replace("\\", "/").rsplit("/", 1)[-1]
    filename = re.sub(r"[\x00-\x1f\x7f]", "", filename)[:180]
    if not filename.lower().endswith(".pdf"):
        raise HTTPException(400, "Chỉ hỗ trợ file PDF.")
    data = await upload.read(MAX_PDF_BYTES + 1)
    if len(data) > MAX_PDF_BYTES:
        raise HTTPException(413, "PDF phải nhỏ hơn hoặc bằng 10 MB.")
    if not data.startswith(b"%PDF-"):
        raise HTTPException(400, "File không có định dạng PDF hợp lệ.")
    return filename, data

def write_temporary_pdf(data):
    with tempfile.NamedTemporaryFile(prefix="yangcareer-cv-", suffix=".pdf", delete=False) as output:
        output.write(data)
        return output.name

def remove_temporary_pdf(path):
    if path:
        try:
            os.unlink(path)
        except FileNotFoundError:
            pass
