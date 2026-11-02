import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

_test_directory = tempfile.TemporaryDirectory()
os.environ["YANGCAREER_DB"] = str(Path(_test_directory.name) / "test.sqlite3")
os.environ.pop("GOOGLE_API_KEY", None)
os.environ["INDEX_ON_STARTUP"] = "0"

from fastapi.testclient import TestClient
from api.main import app
from api.db_utils import get_db_connection

class WorkspaceTests(unittest.TestCase):
    def setUp(self):
        self.a = self.enterContext(TestClient(app))
        self.b = self.enterContext(TestClient(app))
        self.csrf_a = self.a.get("/workspace").json()["csrf_token"]
        self.csrf_b = self.b.get("/workspace").json()["csrf_token"]

    def create_cv(self, client=None, csrf=None, name="Demo student"):
        client = client or self.a
        response = client.post("/cvs/manual", headers={"X-CSRF-Token": csrf or self.csrf_a},
                               json={"name": name, "skills": ["Java", "SQL"], "career_objective": "Backend internship"})
        self.assertEqual(response.status_code, 201, response.text)
        return response.json()["cv_id"]

    def test_public_browsing_and_static_frontend(self):
        self.assertEqual(self.a.get("/health").json()["status"], "ok")
        self.assertFalse(self.a.get("/health").json()["ai_configured"])
        self.assertEqual(self.a.get("/jobs").json()["total"], 6)
        self.assertEqual(self.a.get("/app/").status_code, 200)

    def test_anonymous_guest_cannot_read_cvs_or_applications(self):
        guest = self.enterContext(TestClient(app))
        for path in ("/cvs", "/list-cvs", "/applications/1", "/preview-doc/1"):
            self.assertEqual(guest.get(path).status_code, 401, path)

    def test_each_workspace_lists_only_its_own_cvs(self):
        first = self.create_cv()
        second = self.create_cv(self.b, self.csrf_b, "Another student")
        self.assertIn(first, [row["id"] for row in self.a.get("/cvs").json()])
        self.assertNotIn(second, [row["id"] for row in self.a.get("/cvs").json()])
        self.assertNotIn(first, [row["id"] for row in self.b.get("/list-cvs").json()])

    def test_cross_workspace_cv_endpoints_return_not_found(self):
        cv_id = self.create_cv()
        for path in (f"/applications/{cv_id}", f"/preview-doc/{cv_id}",
                     f"/preview-doc-info/{cv_id}", f"/cv/{cv_id}/insights"):
            self.assertEqual(self.b.get(path).status_code, 404, path)
        for path, data in (
            ("/delete-cv", {"file_id": cv_id}),
            ("/jobs/search", {"cv_id": cv_id}),
            ("/apply", {"cv_id": cv_id, "job_id": 1}),
        ):
            result = self.b.post(path, json=data, headers={"X-CSRF-Token": self.csrf_b})
            self.assertEqual(result.status_code, 404, result.text)

    def test_csrf_and_origin_are_checked(self):
        body = {"name": "Example", "skills": ["Java"]}
        self.assertEqual(self.a.post("/cvs/manual", json=body).status_code, 403)
        result = self.a.post("/cvs/manual", json=body,
                             headers={"X-CSRF-Token": self.csrf_a, "Origin": "https://untrusted.invalid"})
        self.assertEqual(result.status_code, 403)

    def test_cookie_and_private_response_headers(self):
        cookie = self.a.get("/workspace")
        self.assertEqual(cookie.headers["cache-control"], "no-store")
        self.assertEqual(self.a.get("/cvs").headers["cache-control"], "no-store")
        new_client = self.enterContext(TestClient(app))
        header = new_client.get("/workspace").headers["set-cookie"].lower()
        self.assertIn("httponly", header)
        self.assertIn("samesite=lax", header)

    def test_bad_pdf_and_oversized_pdf_are_rejected(self):
        header = {"X-CSRF-Token": self.csrf_a}
        result = self.a.post("/upload-cv", files={"file": ("bad.pdf", b"plain text", "application/pdf")}, headers=header)
        self.assertEqual(result.status_code, 400, result.text)
        result = self.a.post("/upload-cv",
                             files={"file": ("large.pdf", b"%PDF-" + b"x" * (10 * 1024 * 1024), "application/pdf")},
                             headers=header)
        self.assertEqual(result.status_code, 413, result.text)

    def test_pdf_temp_path_is_generated_and_removed(self):
        paths = []
        def fake_text(path):
            paths.append(path)
            self.assertTrue(Path(path).exists())
            return "Java and SQL"
        with patch("api.main.ensure_ai"), patch("api.main.extract_text_from_pdf", side_effect=fake_text), \
             patch("api.main.extract_cv_info", return_value={"name": "Example", "skills": ["Java"], "education": [], "experience": []}):
            result = self.a.post("/upload-cv", headers={"X-CSRF-Token": self.csrf_a},
                                 files={"file": ("../../escape.pdf", b"%PDF-1.7\nexample", "application/pdf")})
        self.assertEqual(result.status_code, 200, result.text)
        cv_id = result.json()["cv_id"]
        self.assertFalse(Path(paths[0]).exists())
        self.assertTrue(Path(paths[0]).name.startswith("yangcareer-cv-"))
        preview = self.a.get(f"/preview-doc/{cv_id}")
        self.assertEqual(preview.content, b"%PDF-1.7\nexample")
        self.assertEqual(self.b.get(f"/preview-doc/{cv_id}").status_code, 404)

    def test_missing_cloud_key_returns_service_unavailable(self):
        result = self.a.post("/upload-cv", headers={"X-CSRF-Token": self.csrf_a},
                             files={"file": ("cv.pdf", b"%PDF-1.7\nexample", "application/pdf")})
        self.assertEqual(result.status_code, 503, result.text)

    def test_duplicate_application_returns_conflict(self):
        cv_id = self.create_cv()
        body = {"cv_id": cv_id, "job_id": 1}
        first = self.a.post("/apply", json=body, headers={"X-CSRF-Token": self.csrf_a})
        self.assertEqual(first.status_code, 200, first.text)
        second = self.a.post("/apply", json=body, headers={"X-CSRF-Token": self.csrf_a})
        self.assertEqual(second.status_code, 409, second.text)

    def test_delete_cv_removes_related_data(self):
        cv_id = self.create_cv()
        result = self.a.post("/apply", json={"cv_id": cv_id, "job_id": 1}, headers={"X-CSRF-Token": self.csrf_a})
        self.assertEqual(result.status_code, 200, result.text)
        result = self.a.post("/delete-cv", json={"file_id": cv_id}, headers={"X-CSRF-Token": self.csrf_a})
        self.assertEqual(result.status_code, 200, result.text)
        with get_db_connection() as conn:
            self.assertEqual(conn.execute("SELECT COUNT(*) FROM applications WHERE cv_id=?", (cv_id,)).fetchone()[0], 0)
            self.assertEqual(conn.execute("SELECT COUNT(*) FROM cv_store WHERE id=?", (cv_id,)).fetchone()[0], 0)

    def test_skill_matching_is_case_insensitive(self):
        cv_id = self.create_cv()
        result = self.a.post("/jobs/search", json={"cv_id": cv_id, "limit": 100},
                             headers={"X-CSRF-Token": self.csrf_a})
        self.assertEqual(result.status_code, 200, result.text)
        backend = next(row for row in result.json()["jobs"] if row["job_title"] == "Backend Intern")
        self.assertEqual(backend["match_score"], 0.5)

    def test_invalid_pagination_is_rejected(self):
        self.assertEqual(self.a.get("/jobs?limit=-1").status_code, 422)
        self.assertEqual(self.a.get("/list-cvs?page=0").status_code, 422)

if __name__ == "__main__":
    unittest.main()
