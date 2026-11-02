"""Ingest → retrieve → present evidence.

Adapted architecturally from patchy631's MIT-licensed RAGWorkflow, accessed through
Trinhvhao/ai-engineering-hub. The original is retained in upstream/workflow.py.
YangDocs replaces the model/global index with scoped SQLite FTS5 and exact quotes.
"""

import hashlib
import io
import re
import secrets
import sqlite3
import time
import unicodedata
from collections import Counter
from pathlib import PurePath

from pypdf import PdfReader

from app.store import Store

MAX_BYTES = 5 * 1024 * 1024
MAX_PAGES = 80
MAX_CHARACTERS = 800_000
MAX_DOCUMENTS = 24
STOPWORDS = set(
    "va la cua trong cho voi cac nhung mot co duoc ve toi ban hay the nao "
    "gi o de khi tu theo nay do tai it a an the is are to for of in on "
    "what how does do can please or and not".split()
)


class DocumentError(Exception):
    def __init__(self, message: str, status: int = 400):
        self.message, self.status = message, status
        super().__init__(message)


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKD", text.lower().replace("đ", "d"))
    return " ".join(
        re.findall(r"[a-z0-9]+", "".join(char for char in text if not unicodedata.combining(char)))
    )


def query_terms(query: str) -> list[str]:
    return list(
        dict.fromkeys(
            word for word in normalize(query).split() if word not in STOPWORDS and len(word) > 1
        )
    )[:16]


def safe_name(filename: str) -> str:
    # Never use a client filename as a disk path, header value, or HTML.
    name = PurePath(filename.replace("\\", "/")).name
    name = "".join(c for c in name if c.isprintable()).strip()[:140]
    if not name or name in (".", ".."):
        raise DocumentError("Tên tài liệu không hợp lệ.")
    return name


def split_chunks(text: str, limit: int = 900, overlap: int = 140):
    text = text.strip()
    start = 0
    while start < len(text):
        end = min(start + limit, len(text))
        if end < len(text):
            boundary = max(
                text.rfind("\n", start + limit // 2, end), text.rfind(" ", start + limit // 2, end)
            )
            if boundary > start:
                end = boundary
        part = text[start:end].strip()
        if part:
            yield part
        if end == len(text):
            break
        start = max(start + 1, end - overlap)


def extract_pages(name: str, data: bytes) -> list[str]:
    if not data:
        raise DocumentError("Tài liệu đang trống.")
    if len(data) > MAX_BYTES:
        raise DocumentError("Mỗi tài liệu tối đa 5 MB.", 413)
    suffix = PurePath(name).suffix.lower()
    if suffix not in (".pdf", ".md", ".txt"):
        raise DocumentError("Chỉ hỗ trợ PDF, Markdown và TXT.", 415)
    if suffix == ".pdf":
        if not data.startswith(b"%PDF-"):
            raise DocumentError("Nội dung không phải PDF hợp lệ.")
        try:
            reader = PdfReader(io.BytesIO(data), strict=True)
            if reader.is_encrypted:
                raise DocumentError("Hãy bỏ mật khẩu PDF trước khi tải lên.")
            if len(reader.pages) > MAX_PAGES:
                raise DocumentError("PDF tối đa 80 trang.")
            pages = []
            size = 0
            for page in reader.pages:
                text = page.extract_text() or ""
                size += len(text)
                if size > MAX_CHARACTERS:
                    raise DocumentError("Tài liệu có quá nhiều nội dung để lập chỉ mục.")
                pages.append(text)
        except DocumentError:
            raise
        except Exception as exc:
            raise DocumentError("Không đọc được PDF này. Hãy kiểm tra lại tệp.") from exc
    else:
        try:
            text = data.decode("utf-8-sig")
        except UnicodeDecodeError as exc:
            raise DocumentError("TXT và Markdown cần được lưu bằng UTF-8.") from exc
        if "\x00" in text:
            raise DocumentError("Tài liệu chứa dữ liệu nhị phân.")
        pages = text.split("\f")
    if len(pages) > MAX_PAGES or sum(map(len, pages)) > MAX_CHARACTERS:
        raise DocumentError("Tài liệu vượt giới hạn 80 trang hoặc 800.000 ký tự.")
    pages = ["".join(c for c in p if c.isprintable() or c in "\n\t").strip() for p in pages]
    if not any(normalize(p) for p in pages):
        raise DocumentError("Không tìm thấy văn bản. PDF ảnh quét cần OCR trước khi tải lên.")
    return pages


class DocumentWorkflow:
    def __init__(self, store: Store):
        self.store = store

    def ingest(self, owner: str, filename: str, data: bytes):
        name = safe_name(filename)
        pages = extract_pages(name, data)
        chunks = [(page, text) for page, body in enumerate(pages, 1) for text in split_chunks(body)]
        document_id = secrets.token_hex(12)
        checksum = hashlib.sha256(data).hexdigest()
        with self.store.connect() as db:
            # Reserve the write lock before checking quotas, including concurrent uploads.
            db.execute("BEGIN IMMEDIATE")
            count = db.execute("SELECT count(*) FROM documents WHERE owner=?", (owner,)).fetchone()[
                0
            ]
            if count >= MAX_DOCUMENTS:
                raise DocumentError("Mỗi workspace tối đa 24 tài liệu.", 409)
            try:
                db.execute(
                    "INSERT INTO documents "
                    "(id,owner,name,kind,size,checksum,page_count,chunk_count,created) "
                    "VALUES (?,?,?,?,?,?,?,?,?)",
                    (
                        document_id,
                        owner,
                        name,
                        PurePath(name).suffix[1:].lower(),
                        len(data),
                        checksum,
                        len(pages),
                        len(chunks),
                        time.time(),
                    ),
                )
            except sqlite3.IntegrityError as exc:
                raise DocumentError("Tài liệu này đã có trong thư viện.", 409) from exc
            db.executemany(
                "INSERT INTO pages VALUES (?,?,?)",
                [(document_id, page, body) for page, body in enumerate(pages, 1)],
            )
            db.executemany(
                "INSERT INTO chunks(document_id,page,text,search_text) VALUES (?,?,?,?)",
                [(document_id, page, body, normalize(name + " " + body)) for page, body in chunks],
            )
        return self.store.document(owner, document_id)

    def retrieve(self, owner: str, query: str, favorites: bool = False):
        terms = query_terms(query)
        if not terms:
            raise DocumentError(
                "Hãy nhập ít nhất một từ khóa có nghĩa, ví dụ: backend hoặc nghỉ phép."
            )
        # Tokenization removes FTS operators, quotes and punctuation before building MATCH.
        expression = " OR ".join(f'"{term}"*' for term in terms)
        with self.store.connect() as db:
            rows = db.execute(
                "SELECT c.id,c.document_id,c.page,c.text,d.name,d.kind,d.favorite,"
                "bm25(chunks_fts) AS rank FROM chunks_fts "
                "JOIN chunks c ON c.id=chunks_fts.rowid JOIN documents d ON d.id=c.document_id "
                "WHERE chunks_fts MATCH ? AND d.owner=? AND (?=0 OR d.favorite=1) "
                "ORDER BY rank LIMIT 64",
                (expression, owner, int(favorites)),
            ).fetchall()
        # Prefer coverage of distinct query terms, then SQLite's BM25 ranking.
        hits = [dict(row) for row in rows]
        for hit in hits:
            words = normalize(hit["name"] + " " + hit["text"]).split()
            hit["coverage"] = sum(any(word.startswith(term) for word in words) for term in terms)
        hits.sort(key=lambda hit: (-hit["coverage"], hit["rank"]))
        # When complete matches exist, suppress unrelated one-word matches.
        required_coverage = (
            len(terms)
            if hits and hits[0]["coverage"] == len(terms)
            else max(1, (len(terms) + 1) // 2)
        )
        hits = [hit for hit in hits if hit["coverage"] >= required_coverage]
        counts = Counter()
        selected = []
        for hit in hits:
            key = (hit["document_id"], hit["page"])
            if counts[key] >= 2:
                continue
            counts[key] += 1
            selected.append(
                {
                    key: hit[key]
                    for key in ("id", "document_id", "page", "text", "name", "kind", "favorite")
                }
            )
            if len(selected) == 8:
                break
        return selected

    def present(self, owner: str, query: str, favorites: bool = False):
        hits = self.retrieve(owner, query, favorites)
        return {
            "query": query,
            "mode": "extractive",
            "terms": query_terms(query),
            "hits": [{**hit, "citation": i} for i, hit in enumerate(hits, 1)],
            "message": (
                "Các đoạn trích nguyên văn, được xếp theo mức liên quan."
                if hits
                else "Chưa tìm thấy đoạn trích phù hợp. Thử từ khóa khác."
            ),
        }
