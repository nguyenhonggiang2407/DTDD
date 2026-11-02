"""SQLite persistence. Ownership is enforced in every public store operation."""

import hashlib
import secrets
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path

SESSION_DAYS = 7

SCHEMA = """
CREATE TABLE IF NOT EXISTS sessions (
    id TEXT PRIMARY KEY, csrf TEXT NOT NULL, expires REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS documents (
    id TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    name TEXT NOT NULL, kind TEXT NOT NULL, size INTEGER NOT NULL, checksum TEXT NOT NULL,
    page_count INTEGER NOT NULL, chunk_count INTEGER NOT NULL,
    favorite INTEGER NOT NULL DEFAULT 0, created REAL NOT NULL,
    UNIQUE(owner, checksum)
);
CREATE TABLE IF NOT EXISTS pages (
    document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    number INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(document_id, number)
);
CREATE TABLE IF NOT EXISTS chunks (
    id INTEGER PRIMARY KEY, document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    page INTEGER NOT NULL, text TEXT NOT NULL, search_text TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS documents_owner ON documents(owner);
CREATE INDEX IF NOT EXISTS chunks_document ON chunks(document_id);
CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
    search_text, content='chunks', content_rowid='id', tokenize='unicode61'
);
CREATE TRIGGER IF NOT EXISTS chunks_insert AFTER INSERT ON chunks BEGIN
    INSERT INTO chunks_fts(rowid, search_text) VALUES (new.id, new.search_text);
END;
CREATE TRIGGER IF NOT EXISTS chunks_delete AFTER DELETE ON chunks BEGIN
    INSERT INTO chunks_fts(chunks_fts, rowid, search_text)
    VALUES ('delete', old.id, old.search_text);
END;
"""


def token_hash(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


class Store:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.path = path
        with self.connect() as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.executescript(SCHEMA)

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA foreign_keys=ON")
        try:
            with db:
                yield db
        finally:
            db.close()

    def session(self, cookie: str | None):
        if not cookie or len(cookie) > 100:
            return None
        with self.connect() as db:
            row = db.execute(
                "SELECT * FROM sessions WHERE id=? AND expires>?",
                (token_hash(cookie), time.time()),
            ).fetchone()
            return dict(row) if row else None

    def new_session(self):
        cookie, csrf = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
        session = {"id": token_hash(cookie), "csrf": csrf}
        with self.connect() as db:
            db.execute("DELETE FROM sessions WHERE expires<=?", (time.time(),))
            db.execute(
                "INSERT INTO sessions VALUES (?, ?, ?)",
                (session["id"], csrf, time.time() + SESSION_DAYS * 86400),
            )
        return cookie, session

    def documents(self, owner: str):
        with self.connect() as db:
            return [
                dict(row)
                for row in db.execute(
                    "SELECT id,name,kind,size,page_count,chunk_count,favorite,created "
                    "FROM documents WHERE owner=? ORDER BY favorite DESC, created DESC",
                    (owner,),
                )
            ]

    def document(self, owner: str, document_id: str):
        with self.connect() as db:
            row = db.execute(
                "SELECT id,name,kind,size,page_count,chunk_count,favorite,created "
                "FROM documents WHERE id=? AND owner=?",
                (document_id, owner),
            ).fetchone()
            return dict(row) if row else None

    def delete(self, owner: str, document_id: str) -> bool:
        with self.connect() as db:
            return (
                db.execute(
                    "DELETE FROM documents WHERE id=? AND owner=?", (document_id, owner)
                ).rowcount
                == 1
            )

    def favorite(self, owner: str, document_id: str, value: bool) -> bool:
        with self.connect() as db:
            return (
                db.execute(
                    "UPDATE documents SET favorite=? WHERE id=? AND owner=?",
                    (int(value), document_id, owner),
                ).rowcount
                == 1
            )

    def page(self, owner: str, document_id: str, number: int):
        with self.connect() as db:
            row = db.execute(
                "SELECT p.text FROM pages p JOIN documents d ON d.id=p.document_id "
                "WHERE d.id=? AND d.owner=? AND p.number=?",
                (document_id, owner, number),
            ).fetchone()
            return row["text"] if row else None
