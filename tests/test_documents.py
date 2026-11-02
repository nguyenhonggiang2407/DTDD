import io
import time

import pytest
from fastapi.testclient import TestClient
from pypdf import PdfWriter
from pypdf.generic import DecodedStreamObject, DictionaryObject, NameObject

from app.main import COOKIE
from app.workflow import MAX_BYTES, MAX_DOCUMENTS, normalize


def upload(client, text="Làm việc từ xa tối đa 2 ngày mỗi tuần.", name="Guide.md"):
    return client.post("/api/documents", files={"file": (name, text.encode(), "text/plain")})


def pdf_bytes(*texts, encrypted=False):
    writer = PdfWriter()
    for text in texts:
        page = writer.add_blank_page(width=400, height=500)
        font = DictionaryObject(
            {
                NameObject("/Type"): NameObject("/Font"),
                NameObject("/Subtype"): NameObject("/Type1"),
                NameObject("/BaseFont"): NameObject("/Helvetica"),
            }
        )
        page[NameObject("/Resources")] = DictionaryObject(
            {NameObject("/Font"): DictionaryObject({NameObject("/F1"): writer._add_object(font)})}
        )
        stream = DecodedStreamObject()
        stream.set_data(f"BT /F1 12 Tf 20 400 Td ({text}) Tj ET".encode())
        page[NameObject("/Contents")] = writer._add_object(stream)
    if encrypted:
        writer.encrypt("test-only-password")
    data = io.BytesIO()
    writer.write(data)
    return data.getvalue()


def test_bootstrap_sets_private_cookie_and_security_headers(client):
    response = client.get("/api/session")
    assert response.json()["retention_days"] == 7
    assert response.headers["cache-control"] == "no-store"
    assert "frame-ancestors 'none'" in response.headers["content-security-policy"]
    assert client.cookies.get(COOKIE)


def test_missing_session_and_csrf_rejected(app, client):
    with TestClient(app) as stranger:
        assert stranger.get("/api/documents").status_code == 401
        response = stranger.get("/api/session")
        assert "HttpOnly" in response.headers["set-cookie"]
        assert "SameSite=strict" in response.headers["set-cookie"]
        assert upload(stranger).status_code == 403
    assert upload(client).status_code == 201


def test_foreign_origin_and_untrusted_host_rejected(client):
    assert client.post("/api/demo", headers={"origin": "https://evil.example"}).status_code == 403
    assert client.get("/health", headers={"host": "evil.example"}).status_code == 400


def test_vietnamese_accent_and_d_stroke_matching(client):
    doc = upload(client, "Đăng ký làm việc từ xa tối đa 2 ngày mỗi tuần.").json()
    response = client.post("/api/search", json={"query": "dang ky lam viec tu xa"}).json()
    assert response["mode"] == "extractive"
    assert response["hits"][0]["document_id"] == doc["id"]
    assert "2 ngày" in response["hits"][0]["text"]
    assert response["hits"][0]["page"] == 1
    assert normalize("Đường dẫn") == "duong dan"


def test_pdf_citation_points_to_actual_page(client):
    result = client.post(
        "/api/documents",
        files={
            "file": ("Handbook.pdf", pdf_bytes("Remote work policy", "Orchid project deadline"))
        },
    )
    assert result.status_code == 201, result.text
    doc = result.json()
    assert doc["page_count"] == 2
    hit = client.post("/api/search", json={"query": "orchid"}).json()["hits"][0]
    assert hit["page"] == 2
    page = client.get(f"/api/documents/{doc['id']}/pages/2").json()
    assert "Orchid project deadline" in page["text"]
    assert client.get(f"/api/documents/{doc['id']}/pages/3").status_code == 404


@pytest.mark.parametrize(
    "name,data,status",
    [
        ("bad.pdf", b"not a pdf", 400),
        ("data.exe", b"executable", 415),
        ("empty.md", b"", 400),
        ("binary.txt", b"hello\x00world", 400),
        ("broken.txt", b"\xff\xfe\x80", 400),
        ("large.txt", b"a" * (MAX_BYTES + 1), 413),
    ],
)
def test_invalid_upload_is_rejected_without_indexing(client, name, data, status):
    response = client.post("/api/documents", files={"file": (name, data)})
    assert response.status_code == status
    assert client.get("/api/documents").json()["stats"]["documents"] == 0


def test_pdf_encrypted_or_scanned_rejected(client):
    for data in (pdf_bytes("secret", encrypted=True), pdf_bytes("")):
        response = client.post("/api/documents", files={"file": ("scan.pdf", data)})
        assert response.status_code == 400
    assert client.get("/api/documents").json()["documents"] == []


def test_filename_is_metadata_never_a_path(client):
    doc = upload(client, name="../../escape.md").json()
    assert doc["name"] == "escape.md"
    assert len(doc["id"]) == 24


def test_duplicate_is_scoped_to_owner(app, client):
    assert upload(client).status_code == 201
    assert upload(client, name="Renamed.md").status_code == 409
    with TestClient(app) as other:
        other.headers["x-csrf-token"] = other.get("/api/session").json()["csrf"]
        assert upload(other).status_code == 201


def test_all_document_operations_are_isolated(app, client):
    doc = upload(client).json()
    with TestClient(app) as other:
        other.headers["x-csrf-token"] = other.get("/api/session").json()["csrf"]
        assert other.get("/api/documents").json()["documents"] == []
        assert other.post("/api/search", json={"query": "lam viec"}).json()["hits"] == []
        assert other.get(f"/api/documents/{doc['id']}/pages/1").status_code == 404
        assert (
            other.patch(f"/api/documents/{doc['id']}", json={"favorite": True}).status_code == 404
        )
        assert other.delete(f"/api/documents/{doc['id']}").status_code == 404
        assert other.post("/api/export", json={"query": "lam viec"}).status_code == 404
    assert client.get("/api/documents").json()["stats"]["documents"] == 1


def test_favorites_filter_and_markdown_export(client):
    doc = upload(client).json()
    payload = {"query": "lam viec", "favorites": True}
    assert client.post("/api/search", json=payload).json()["hits"] == []
    assert client.patch(f"/api/documents/{doc['id']}", json={"favorite": True}).status_code == 200
    assert len(client.post("/api/search", json=payload).json()["hits"]) == 1
    exported = client.post("/api/export", json=payload)
    assert "Guide.md" in exported.text and "Trang 1" in exported.text
    assert "> Làm việc từ xa" in exported.text
    assert "attachment" in exported.headers["content-disposition"]


def test_delete_cascades_into_pages_and_fts(client, app):
    doc = upload(client).json()
    assert client.delete(f"/api/documents/{doc['id']}").status_code == 204
    assert client.post("/api/search", json={"query": "lam viec"}).json()["hits"] == []
    with app.state.store.connect() as db:
        assert db.execute("SELECT count(*) FROM pages").fetchone()[0] == 0
        assert db.execute("SELECT count(*) FROM chunks").fetchone()[0] == 0
        assert (
            db.execute("SELECT count(*) FROM chunks_fts WHERE chunks_fts MATCH 'lam'").fetchone()[0]
            == 0
        )
    assert client.delete(f"/api/documents/{doc['id']}").status_code == 404


@pytest.mark.parametrize("query", ["  ", "a", "x" * 201, "và của trong", '" OR * : -'])
def test_invalid_queries_are_safe(client, query):
    assert client.post("/api/search", json={"query": query}).status_code in (400, 422)


def test_sample_import_is_repeatable_and_never_global(app, client):
    assert client.post("/api/demo").json()["added"] == 3
    assert client.post("/api/demo").json()["added"] == 0
    assert client.get("/api/documents").json()["stats"]["documents"] == 3
    with TestClient(app) as other:
        other.get("/api/session")
        assert other.get("/api/documents").json()["documents"] == []


def test_document_quota_is_enforced(client):
    for i in range(MAX_DOCUMENTS):
        assert upload(client, f"Backend document number {i}", f"{i}.md").status_code == 201
    assert upload(client, "Another unique text").status_code == 409


def test_expired_sessions_cannot_read_and_are_cleaned(app, client):
    upload(client)
    with app.state.store.connect() as db:
        db.execute("UPDATE sessions SET expires=?", (time.time() - 1,))
    assert client.get("/api/documents").status_code == 401
    client.get("/api/session")
    assert client.get("/api/documents").json()["documents"] == []
    with app.state.store.connect() as db:
        assert db.execute("SELECT count(*) FROM chunks").fetchone()[0] == 0


def test_request_limit_applies_even_before_multipart_parser(client):
    response = client.post(
        "/api/documents",
        content=b"a" * (MAX_BYTES + 70_000),
        headers={"content-type": "multipart/form-data; boundary=test"},
    )
    assert response.status_code == 413


def test_private_db_and_examples_are_not_served(client):
    assert client.get("/data/yangdocs.sqlite3").status_code == 404
    assert client.get("/examples/Remote-work.md").status_code == 404
