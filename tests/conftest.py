import pytest
from fastapi.testclient import TestClient

from app.main import create_app


@pytest.fixture
def app(tmp_path):
    return create_app(tmp_path / "test.sqlite3")


@pytest.fixture
def client(app):
    with TestClient(app) as client:
        csrf = client.get("/api/session").json()["csrf"]
        client.headers["x-csrf-token"] = csrf
        yield client
