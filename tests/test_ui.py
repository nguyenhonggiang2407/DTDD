"""Real browser integration tests. Run separately after `playwright install chromium`."""

import os
import socket
import subprocess
import sys
import time
from pathlib import Path

import httpx
import pytest
from playwright.sync_api import expect, sync_playwright

ROOT = Path(__file__).resolve().parents[1]
SCREENSHOTS = ROOT / "docs/screenshots"


@pytest.fixture(scope="module")
def server(tmp_path_factory):
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    env = {**os.environ, "YANGDOCS_DB": str(tmp_path_factory.mktemp("ui") / "ui.sqlite3")}
    process = subprocess.Popen(
        [
            sys.executable,
            "-m",
            "uvicorn",
            "app.main:app",
            "--host",
            "127.0.0.1",
            "--port",
            str(port),
        ],
        cwd=ROOT,
        env=env,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )
    url = f"http://127.0.0.1:{port}"
    try:
        for _ in range(100):
            if process.poll() is not None:
                raise RuntimeError(process.stderr.read().decode())
            try:
                if httpx.get(url + "/health", timeout=1).status_code == 200:
                    break
            except httpx.HTTPError:
                pass
            time.sleep(0.1)
        else:
            raise RuntimeError("Test server did not start")
        yield url
    finally:
        process.terminate()
        process.wait(timeout=10)


@pytest.fixture(scope="module")
def browser():
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        yield browser
        browser.close()


def test_desktop_search_citation_export_and_screenshot(browser, server, tmp_path):
    context = browser.new_context(viewport={"width": 1440, "height": 1050})
    page = context.new_page()
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(server)
    expect(page.get_by_text("Bắt đầu từ một tài liệu.", exact=True)).to_be_visible()
    page.get_by_role("button", name="Thử bộ tài liệu mẫu", exact=True).click()
    expect(page.locator(".document-card")).to_have_count(3)
    expect(page.locator("#stat-pages")).to_have_text("3")
    expect(page.locator("#toast")).not_to_be_visible(timeout=6000)
    SCREENSHOTS.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(SCREENSHOTS / "desktop.png"), full_page=True)

    page.get_by_label("Từ khóa tìm kiếm").fill("lam viec tu xa")
    page.get_by_role("button", name="Tìm kiếm", exact=True).click()
    expect(page.locator(".result-card").first).to_be_visible()
    expect(page.locator(".result-card").first).to_contain_text("Remote-work.md")
    expect(page.locator(".result-card").first).to_contain_text("Trang 1")
    page.screenshot(path=str(SCREENSHOTS / "search.png"), full_page=True)
    page.get_by_role("button", name="Mở nguồn Remote-work.md trang 1").first.click()
    expect(page.get_by_role("dialog", name="Remote-work.md")).to_be_visible()
    expect(page.locator("#preview-text")).to_contain_text("tối đa 2 ngày")
    page.get_by_role("button", name="Đóng tài liệu", exact=True).click()
    with page.expect_download() as download:
        page.get_by_role("button", name="Xuất ghi chú").click()
    target = tmp_path / "notes.md"
    download.value.save_as(target)
    assert "Remote-work.md" in target.read_text()
    assert "Trang 1" in target.read_text()
    assert errors == []
    context.close()


def test_mobile_upload_safe_render_pin_preview_and_delete(browser, server):
    context = browser.new_context(viewport={"width": 390, "height": 844}, is_mobile=True)
    page = context.new_page()
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(server)
    expect(page.get_by_text("Bắt đầu từ một tài liệu.", exact=True)).to_be_visible()
    page.get_by_role("button", name="Thêm tài liệu", exact=True).click()
    payload = (
        "# Mobile note\nBackend review: <img src=x onerror=alert(1)> là văn bản, không phải HTML."
    )
    page.locator("#file-input").set_input_files(
        {
            "name": "Mobile-note.md",
            "mimeType": "text/markdown",
            "buffer": payload.encode(),
        }
    )
    page.get_by_role("button", name="Thêm vào thư viện", exact=False).click()
    expect(page.locator("#upload-dialog")).not_to_be_visible()
    expect(page.locator(".document-card")).to_have_count(1)
    page.get_by_role("button", name="Ghim Mobile-note.md", exact=True).click()
    expect(page.locator("#stat-favorites")).to_have_text("1")
    page.get_by_role("button", name="Đã ghim", exact=False).click()
    expect(page.locator(".document-card")).to_have_count(1)
    page.get_by_role("button", name="Mở Mobile-note.md", exact=True).click()
    expect(page.locator("#preview-text")).to_contain_text("<img src=x onerror=alert(1)>")
    assert page.locator("#preview-text img").count() == 0
    page.get_by_role("button", name="Đóng tài liệu", exact=True).click()
    page.get_by_role("button", name="Xóa Mobile-note.md", exact=True).click()
    page.get_by_role("button", name="Giữ lại", exact=True).click()
    expect(page.locator(".document-card")).to_have_count(1)
    page.get_by_role("button", name="Xóa Mobile-note.md", exact=True).click()
    page.get_by_role("button", name="Xóa tài liệu", exact=True).click()
    expect(page.locator(".document-card")).to_have_count(0)

    page.get_by_role("button", name="Thư viện", exact=False).click()
    page.get_by_role("button", name="Thử bộ tài liệu mẫu", exact=True).click()
    expect(page.locator(".document-card")).to_have_count(3)
    expect(page.locator("#toast")).not_to_be_visible(timeout=6000)
    SCREENSHOTS.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(SCREENSHOTS / "mobile.png"), full_page=True)
    # DOM measurement validates actual horizontal overflow, including hidden dialog handling.
    assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
    assert errors == []
    context.close()


def test_ui_error_empty_query_and_filter(browser, server):
    context = browser.new_context(viewport={"width": 1024, "height": 800})
    page = context.new_page()
    page.goto(server)
    expect(page.get_by_text("Bắt đầu từ một tài liệu.", exact=True)).to_be_visible()
    page.get_by_role("button", name="Thử bộ tài liệu mẫu", exact=True).click()
    expect(page.locator(".document-card")).to_have_count(3)
    page.get_by_label("Lọc định dạng").select_option("pdf")
    expect(page.get_by_text("Chưa có định dạng này", exact=True)).to_be_visible()
    page.get_by_label("Lọc định dạng").select_option("all")
    page.get_by_label("Từ khóa tìm kiếm").fill("quasar_unfindable")
    page.get_by_role("button", name="Tìm kiếm", exact=True).click()
    expect(page.get_by_text("Chưa tìm thấy nội dung phù hợp.", exact=True)).to_be_visible()
    expect(page.get_by_role("button", name="Xuất ghi chú")).to_be_disabled()
    page.get_by_role("button", name="Đóng kết quả tìm kiếm").click()
    page.get_by_role("button", name="Thêm tài liệu", exact=True).click()
    page.locator("#file-input").set_input_files(
        {
            "name": "broken.pdf",
            "mimeType": "application/pdf",
            "buffer": b"not a PDF",
        }
    )
    page.get_by_role("button", name="Thêm vào thư viện", exact=False).click()
    expect(page.locator("#upload-message")).to_contain_text("không phải PDF hợp lệ")
    assert page.locator(".document-card").count() == 3
    context.close()
