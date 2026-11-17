"""CourtMate Cloud pilot: centralized reservations, staff approvals, verified-gateway adapter.

Uses one SQLite server DB, NOT device-local DB sync. Run behind HTTPS reverse proxy.
No money is marked settled from a QR scan or client confirmation.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import threading
import time
from contextlib import contextmanager, asynccontextmanager
from collections import defaultdict, deque
from datetime import date as Date, datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo
from typing import Literal
from urllib.parse import quote

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, field_validator

ROOT = Path(__file__).resolve().parent
DATABASE = lambda: Path(os.getenv("COURTMATE_DB", str(ROOT / "courtmate_cloud.db")))
ADMIN_TOKEN = lambda: os.getenv("COURTMATE_ADMIN_TOKEN", "")
WEBHOOK_SECRET = lambda: os.getenv("COURTMATE_GATEWAY_WEBHOOK_SECRET", "")
PAYMENT_TEMPLATE = lambda: os.getenv("COURTMATE_PAYMENT_URL_TEMPLATE", "")
PORTAL_ENABLED = lambda: os.getenv("COURTMATE_PUBLIC_BOOKING", "false").lower() == "true"
WEBHOOK_WINDOW_S = 300
VIETNAM_TZ = ZoneInfo("Asia/Ho_Chi_Minh")
@asynccontextmanager
async def lifespan(_app: FastAPI):
    init_db()
    yield

app = FastAPI(title="CourtMate Cloud pilot", version="0.3.0", lifespan=lifespan)
app.mount("/static", StaticFiles(directory=str(ROOT / "static")), name="static")
_public_attempts = defaultdict(deque)
_public_lock = threading.Lock()


@app.middleware("http")
async def prevent_sensitive_caching(request: Request, call_next):
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["Referrer-Policy"] = "no-referrer"
    if request.url.path.startswith("/v1/"):
        response.headers["Cache-Control"] = "no-store"
    return response


def limit_public_bookings(request: Request):
    # Pilot rate limit; use reverse proxy/WAF in production. Never trust arbitrary X-Forwarded-For.
    ip = request.client.host if request.client else "unknown"
    now = time.monotonic()
    with _public_lock:
        history = _public_attempts[ip]
        while history and now - history[0] > 60:
            history.popleft()
        if len(history) >= 30:
            raise HTTPException(429, "Quá nhiều yêu cầu. Vui lòng thử lại sau")
        history.append(now)


@contextmanager
def connection():
    path = DATABASE()
    path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(str(path), timeout=20, isolation_level=None, check_same_thread=False)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA foreign_keys=ON")
    db.execute("PRAGMA busy_timeout=20000")
    try:
        yield db
    finally:
        db.close()


def init_db():
    with connection() as db:
        db.execute("PRAGMA journal_mode=WAL")
        db.executescript('''
        CREATE TABLE IF NOT EXISTS courts (
            id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL,
            price_hour INTEGER NOT NULL CHECK(price_hour>=0), active INTEGER NOT NULL DEFAULT 1);
        CREATE TABLE IF NOT EXISTS bookings (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            court_id INTEGER NOT NULL REFERENCES courts(id),
            date TEXT NOT NULL, start TEXT NOT NULL, end TEXT NOT NULL,
            customer_name TEXT NOT NULL, customer_phone TEXT NOT NULL,
            amount INTEGER NOT NULL CHECK(amount>=0),
            status TEXT NOT NULL CHECK(status IN ('PENDING','CONFIRMED','CANCELLED')),
            payment_status TEXT NOT NULL CHECK(payment_status IN ('UNPAID','BANK_PENDING','PAID')),
            payment_reference TEXT UNIQUE,
            public_token TEXT UNIQUE NOT NULL,
            payment_url TEXT,
            paid_at TEXT,
            created_at TEXT NOT NULL,
            hold_until TEXT NOT NULL,
            updated_at TEXT NOT NULL);
        CREATE INDEX IF NOT EXISTS idx_booking_slot ON bookings(court_id,date,status,start,end);
        CREATE TABLE IF NOT EXISTS payment_events (
            event_id TEXT PRIMARY KEY, booking_id INTEGER NOT NULL REFERENCES bookings(id),
            provider_reference TEXT NOT NULL, amount INTEGER NOT NULL,
            event_type TEXT NOT NULL, created_at TEXT NOT NULL);
        ''')


def require_admin(authorization: str | None = Header(default=None)):
    secret = ADMIN_TOKEN()
    if len(secret) < 32:
        raise HTTPException(status_code=503, detail="Cần cấu hình COURTMATE_ADMIN_TOKEN tối thiểu 32 ký tự")
    supplied = (authorization or "").removeprefix("Bearer ")
    if not hmac.compare_digest(supplied, secret):
        raise HTTPException(status_code=401, detail="Token quản trị không hợp lệ")
    return True


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def expire_holds(db):
    now = iso_now()
    db.execute("UPDATE bookings SET status='CANCELLED', updated_at=? "
               "WHERE status='PENDING' AND payment_status!='PAID' AND hold_until<?", (now, now))


class CourtCreate(BaseModel):
    name: str = Field(min_length=2, max_length=100)
    price_hour: int = Field(ge=0, le=100_000_000)


class BookingCreate(BaseModel):
    court_id: int = Field(ge=1)
    date: str
    start: str
    end: str
    customer_name: str = Field(min_length=2, max_length=100)
    customer_phone: str = Field(pattern=r"^[0-9]{9,11}$")

    @field_validator("date")
    @classmethod
    def date_iso(cls, value):
        try:
            d = Date.fromisoformat(value)
            if d.isoformat() != value:
                raise ValueError()
            if not datetime.now(VIETNAM_TZ).date() <= d <= datetime.now(VIETNAM_TZ).date() + timedelta(days=365):
                raise ValueError("Ngày đặt sân ngoài giới hạn 0–365 ngày")
        except ValueError as ex:
            raise ValueError("Ngày phải là YYYY-MM-DD và không ở quá khứ") from ex
        return value

    @field_validator("start", "end")
    @classmethod
    def time_hhmm(cls, value):
        if not re.fullmatch(r"(?:[01][0-9]|2[0-3]):[0-5][0-9]", value):
            raise ValueError("Giờ phải ở định dạng HH:mm")
        return value


def booking_to_dict(row):
    return dict(row)


def create_booking(payload: BookingCreate, public: bool):
    if payload.end <= payload.start:
        raise HTTPException(422, "Giờ kết thúc phải lớn hơn giờ bắt đầu; chưa hỗ trợ qua 0h")
    # A same-day booking must not reserve a slot that has already started.
    local_now = datetime.now(VIETNAM_TZ)
    if payload.date == local_now.date().isoformat() and payload.start <= local_now.strftime("%H:%M"):
        raise HTTPException(422, "Giờ bắt đầu phải ở tương lai (múi giờ Việt Nam)")
    if (payload.customer_name != payload.customer_name.strip()):
        raise HTTPException(422, "Tên khách không được chứa khoảng trắng đầu/cuối")
    minutes = (int(payload.end[:2])*60+int(payload.end[3:])
               -int(payload.start[:2])*60-int(payload.start[3:]))
    if minutes < 30 or minutes > 480:
        raise HTTPException(422, "Thời lượng đặt phải từ 30 phút đến 8 giờ")
    with connection() as db:
        db.execute("BEGIN IMMEDIATE")
        try:
            expire_holds(db)
            court = db.execute("SELECT * FROM courts WHERE id=? AND active=1", (payload.court_id,)).fetchone()
            if court is None:
                raise HTTPException(404, "Sân không tồn tại hoặc đang bảo trì")
            overlap = db.execute("SELECT id FROM bookings WHERE court_id=? AND date=? "
                                 "AND status IN ('PENDING','CONFIRMED') AND start<? AND end>? LIMIT 1",
                                 (payload.court_id, payload.date, payload.end, payload.start)).fetchone()
            if overlap:
                raise HTTPException(409, "Khung giờ này đã có người đặt")
            amount = (court["price_hour"] * minutes + 59)//60
            now = iso_now()
            hold = (datetime.now(timezone.utc)
                    + timedelta(minutes=20 if public else 24*60*365)).isoformat(timespec="seconds")
            status = "PENDING" if public else "CONFIRMED"
            cur = db.execute('''INSERT INTO bookings
               (court_id,date,start,end,customer_name,customer_phone,amount,status,
               payment_status,created_at,hold_until,updated_at,public_token)
               VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)''',
               (payload.court_id, payload.date, payload.start, payload.end,
                payload.customer_name, payload.customer_phone, amount, status,
                "UNPAID", now, hold, now, secrets.token_urlsafe(24)))
            row = db.execute("SELECT * FROM bookings WHERE id=?", (cur.lastrowid,)).fetchone()
            db.execute("COMMIT")
            return booking_to_dict(row)
        except Exception:
            db.execute("ROLLBACK")
            raise


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/", response_class=HTMLResponse)
def home():
    return (ROOT / "static" / "index.html").read_text(encoding="utf-8")


@app.get("/v1/public/courts")
def public_courts():
    if not PORTAL_ENABLED():
        raise HTTPException(503, "Cổng đặt sân công khai chưa bật")
    with connection() as db:
        return [dict(row) for row in db.execute("SELECT * FROM courts WHERE active=1 ORDER BY id")]


@app.get("/v1/public/availability")
def availability(court_id: int, date: str):
    if not PORTAL_ENABLED():
        raise HTTPException(503, "Cổng đặt sân công khai chưa bật")
    try:
        parsed = Date.fromisoformat(date)
        if parsed.isoformat() != date:
            raise ValueError()
    except ValueError:
        raise HTTPException(422, "Ngày không hợp lệ")
    with connection() as db:
        db.execute("BEGIN IMMEDIATE")
        try:
            active_court = db.execute("SELECT id FROM courts WHERE id=? AND active=1", (court_id,)).fetchone()
            if not active_court:
                raise HTTPException(404, "Sân không tồn tại hoặc đang tạm đóng")
            expire_holds(db)
            rows = db.execute("SELECT start,end FROM bookings WHERE court_id=? AND date=? "
                              "AND status IN ('PENDING','CONFIRMED') ORDER BY start",
                              (court_id,date)).fetchall()
            result = [dict(r) for r in rows]
            db.execute("COMMIT")
            return result
        except Exception:
            db.execute("ROLLBACK")
            raise


@app.post("/v1/public/bookings", status_code=201)
def public_book(payload: BookingCreate, request: Request):
    if not PORTAL_ENABLED():
        raise HTTPException(503, "Cổng đặt sân công khai chưa bật")
    limit_public_bookings(request)
    booking = create_booking(payload, True)
    # Do NOT return PII, privileged IDs or payment URLs in a public response.
    return {"id": booking["id"], "status": booking["status"],
            "amount": booking["amount"], "hold_until": booking["hold_until"],
            "access_token": booking["public_token"]}


class BookingLookup(BaseModel):
    id: int = Field(ge=1)
    access_token: str = Field(min_length=30, max_length=100)


@app.post("/v1/public/booking-status")
def public_booking_status(query: BookingLookup, request: Request):
    if not PORTAL_ENABLED():
        raise HTTPException(503, "Cổng đặt sân công khai chưa bật")
    limit_public_bookings(request)
    with connection() as db:
        db.execute("BEGIN IMMEDIATE")
        try:
            expire_holds(db)
            row = db.execute("SELECT status,payment_status,amount,payment_url,hold_until "
                             "FROM bookings WHERE id=? AND public_token=?",
                             (query.id,query.access_token)).fetchone()
            if not row:
                raise HTTPException(404, "Không tìm thấy yêu cầu")
            result = dict(row)
            # Payment URL is only visible to the holder of the random private token.
            db.execute("COMMIT")
            return result
        except Exception:
            db.execute("ROLLBACK")
            raise


@app.post("/v1/public/bookings/cancel")
def public_cancel_booking(query: BookingLookup, request: Request):
    """Only the holder of the secret booking token may cancel an unpaid, pending hold."""
    if not PORTAL_ENABLED():
        raise HTTPException(503, "Cổng đặt sân công khai chưa bật")
    limit_public_bookings(request)
    with connection() as db:
        db.execute("BEGIN IMMEDIATE")
        try:
            expire_holds(db)
            row = db.execute("SELECT status,payment_status FROM bookings "
                             "WHERE id=? AND public_token=?", (query.id, query.access_token)).fetchone()
            if row is None:
                raise HTTPException(404, "Không tìm thấy yêu cầu")
            if row["status"] == "CANCELLED":
                db.execute("COMMIT")
                return {"id": query.id, "status": "CANCELLED"}
            if row["status"] != "PENDING" or row["payment_status"] != "UNPAID":
                raise HTTPException(409, "Lịch đã xác nhận hoặc có thanh toán; liên hệ chủ sân để hủy")
            db.execute("UPDATE bookings SET status='CANCELLED',updated_at=? WHERE id=?",
                       (iso_now(), query.id))
            db.execute("COMMIT")
            return {"id": query.id, "status": "CANCELLED"}
        except Exception:
            db.execute("ROLLBACK")
            raise


@app.get("/v1/admin/courts", dependencies=[Depends(require_admin)])
def admin_courts():
    with connection() as db:
        return [dict(r) for r in db.execute("SELECT * FROM courts ORDER BY id")]


@app.post("/v1/admin/courts", status_code=201, dependencies=[Depends(require_admin)])
def admin_add_court(payload: CourtCreate):
    name = payload.name.strip()
    if len(name) < 2:
        raise HTTPException(422, "Tên sân quá ngắn")
    with connection() as db:
        try:
            cur = db.execute("INSERT INTO courts(name,price_hour) VALUES(?,?)",
                             (name, payload.price_hour))
            return {"id": cur.lastrowid, "name": name, "price_hour": payload.price_hour}
        except sqlite3.IntegrityError:
            raise HTTPException(409, "Trùng tên sân")


class CourtUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=2, max_length=100)
    price_hour: int | None = Field(default=None, ge=0, le=100_000_000)
    active: bool | None = None


@app.patch("/v1/admin/courts/{court_id}", dependencies=[Depends(require_admin)])
def admin_update_court(court_id: int, payload: CourtUpdate):
    updates = payload.model_dump(exclude_unset=True)
    if not updates or any(value is None for value in updates.values()):
        raise HTTPException(422, "Cần ít nhất một thuộc tính hợp lệ")
    if "name" in updates:
        updates["name"] = updates["name"].strip()
        if len(updates["name"]) < 2:
            raise HTTPException(422, "Tên sân quá ngắn")
    if "active" in updates:
        updates["active"] = int(updates["active"])
    with connection() as db:
        db.execute("BEGIN IMMEDIATE")
        try:
            if not db.execute("SELECT id FROM courts WHERE id=?", (court_id,)).fetchone():
                raise HTTPException(404, "Sân không tồn tại")
            columns = ", ".join(f"{column}=?" for column in updates)
            db.execute(f"UPDATE courts SET {columns} WHERE id=?", (*updates.values(), court_id))
            court = dict(db.execute("SELECT * FROM courts WHERE id=?", (court_id,)).fetchone())
            db.execute("COMMIT")
            return court
        except sqlite3.IntegrityError:
            db.execute("ROLLBACK")
            raise HTTPException(409, "Trùng tên sân")
        except Exception:
            db.execute("ROLLBACK")
            raise


@app.post("/v1/admin/courts/{court_id}/update", dependencies=[Depends(require_admin)])
def admin_update_court_post(court_id: int, payload: CourtUpdate):
    # HttpURLConnection on Android may not support the PATCH verb.
    return admin_update_court(court_id, payload)


@app.get("/v1/admin/summary", dependencies=[Depends(require_admin)])
def admin_summary(date: str | None = None):
    """Daily operational totals for the canonical *cloud* bookings only."""
    selected = date or datetime.now(VIETNAM_TZ).date().isoformat()
    try:
        if Date.fromisoformat(selected).isoformat() != selected:
            raise ValueError()
    except ValueError:
        raise HTTPException(422, "Ngày phải là YYYY-MM-DD")
    with connection() as db:
        db.execute("BEGIN IMMEDIATE")
        try:
            expire_holds(db)
            counts = db.execute("""SELECT
                COUNT(*) AS total_bookings,
                COALESCE(SUM(CASE WHEN status='PENDING' THEN 1 ELSE 0 END),0) AS pending,
                COALESCE(SUM(CASE WHEN status='CONFIRMED' THEN 1 ELSE 0 END),0) AS confirmed,
                COALESCE(SUM(CASE WHEN status='CANCELLED' THEN 1 ELSE 0 END),0) AS cancelled,
                COALESCE(SUM(CASE WHEN payment_status='PAID' THEN amount ELSE 0 END),0) AS paid_amount,
                COALESCE(SUM(CASE WHEN status='CONFIRMED' AND payment_status!='PAID'
                                  THEN amount ELSE 0 END),0) AS amount_to_collect
                FROM bookings WHERE date=?""", (selected,)).fetchone()
            active_courts = db.execute("SELECT COUNT(*) FROM courts WHERE active=1").fetchone()[0]
            db.execute("COMMIT")
            return {"date": selected, "active_courts": active_courts, **dict(counts)}
        except Exception:
            db.execute("ROLLBACK")
            raise


@app.get("/v1/admin/bookings", dependencies=[Depends(require_admin)])
def admin_bookings(date: str | None = None):
    with connection() as db:
        db.execute("BEGIN IMMEDIATE")
        try:
            expire_holds(db)
            sql = "SELECT b.*, c.name AS court_name FROM bookings b JOIN courts c ON b.court_id=c.id"
            params = []
            if date:
                try:
                    if Date.fromisoformat(date).isoformat() != date:
                        raise ValueError()
                except ValueError:
                    raise HTTPException(422, "Ngày phải là YYYY-MM-DD")
                sql += " WHERE b.date=?"
                params.append(date)
            sql += " ORDER BY b.date DESC, b.start ASC, b.id DESC LIMIT 200"
            rows = [dict(r) for r in db.execute(sql, params)]
            # Public access tokens are customer secrets, never return them in staff lists.
            for row in rows:
                row.pop("public_token", None)
            db.execute("COMMIT")
            return rows
        except Exception:
            db.execute("ROLLBACK")
            raise


@app.post("/v1/admin/bookings", status_code=201, dependencies=[Depends(require_admin)])
def admin_book(payload: BookingCreate):
    result = create_booking(payload, False)
    result.pop("public_token", None)
    return result


class BookingStatus(BaseModel):
    status: Literal["CONFIRMED", "CANCELLED"]


@app.patch("/v1/admin/bookings/{booking_id}", dependencies=[Depends(require_admin)])
def update_booking(booking_id: int, payload: BookingStatus):
    with connection() as db:
        db.execute("BEGIN IMMEDIATE")
        try:
            row = db.execute("SELECT * FROM bookings WHERE id=?", (booking_id,)).fetchone()
            if not row:
                raise HTTPException(404, "Không tìm thấy lịch")
            if row["payment_status"] in ("PAID", "BANK_PENDING") and payload.status == "CANCELLED":
                raise HTTPException(409, "Lịch đang/đã thanh toán; cần hoàn tiền hoặc đối soát riêng")
            if row["status"] == "CANCELLED" and payload.status == "CONFIRMED":
                raise HTTPException(409, "Không mở lại lịch đã hủy")
            db.execute("UPDATE bookings SET status=?, updated_at=? WHERE id=?",
                       (payload.status, iso_now(), booking_id))
            db.execute("COMMIT")
            return {"id":booking_id, "status":payload.status}
        except Exception:
            db.execute("ROLLBACK")
            raise


@app.post("/v1/admin/bookings/{booking_id}/status", dependencies=[Depends(require_admin)])
def update_booking_post(booking_id: int, payload: BookingStatus):
    return update_booking(booking_id, payload)


@app.post("/v1/admin/bookings/{booking_id}/bank-intent", dependencies=[Depends(require_admin)])
def create_bank_intent(booking_id: int):
    if not PAYMENT_TEMPLATE() or "{reference}" not in PAYMENT_TEMPLATE() or "{amount}" not in PAYMENT_TEMPLATE():
        raise HTTPException(503, "Chưa tích hợp URL thanh toán của đối tác ngân hàng/PSP")
    if len(WEBHOOK_SECRET()) < 32:
        raise HTTPException(503, "Cần secret xác thực webhook phía máy chủ")
    with connection() as db:
        db.execute("BEGIN IMMEDIATE")
        try:
            row = db.execute("SELECT * FROM bookings WHERE id=?", (booking_id,)).fetchone()
            if not row:
                raise HTTPException(404, "Không tìm thấy lịch")
            if row["status"] != "CONFIRMED" or row["payment_status"] == "PAID":
                raise HTTPException(409, "Chỉ tạo thanh toán cho lịch xác nhận, chưa trả")
            reference = row["payment_reference"] or ("CM"+str(booking_id)+"-"+secrets.token_hex(5).upper())
            # URL TEMPLATE is an integration adapter, not a bank guarantee.
            url = PAYMENT_TEMPLATE().replace("{reference}", quote(reference)).replace("{amount}",str(row["amount"]))
            if not url.startswith("https://"):
                raise HTTPException(503, "Payment URL phải dùng HTTPS")
            db.execute("UPDATE bookings SET payment_reference=?,payment_url=?, "
                       "payment_status='BANK_PENDING', updated_at=? WHERE id=?",
                       (reference,url,iso_now(),booking_id))
            db.execute("COMMIT")
            return {"booking_id":booking_id,"reference":reference,"amount":row["amount"],
                    "payment_url":url,"status":"BANK_PENDING"}
        except Exception:
            db.execute("ROLLBACK")
            raise


class PaymentEvent(BaseModel):
    event_id: str = Field(min_length=8, max_length=100)
    reference: str = Field(min_length=4, max_length=100)
    amount: int = Field(ge=0)
    currency: Literal["VND"]
    status: Literal["SUCCESS", "FAILED"]


@app.post("/v1/provider/webhooks/payment")
async def verified_bank_webhook(request: Request,
                                x_courtmate_timestamp: str | None = Header(default=None),
                                x_courtmate_signature: str | None = Header(default=None)):
    secret = WEBHOOK_SECRET()
    if len(secret) < 32:
        raise HTTPException(503, "Webhook chưa được cấu hình")
    raw = await request.body()
    if len(raw) > 4096:
        raise HTTPException(413, "Payload quá lớn")
    try:
        timestamp = int(x_courtmate_timestamp or "")
    except ValueError:
        raise HTTPException(401, "Timestamp không hợp lệ")
    if abs(int(time.time()) - timestamp) > WEBHOOK_WINDOW_S:
        raise HTTPException(401, "Webhook đã hết hạn")
    expected = hmac.new(secret.encode(), f"{timestamp}.".encode()+raw, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, x_courtmate_signature or ""):
        raise HTTPException(401, "Webhook signature không hợp lệ")
    try:
        event = PaymentEvent.model_validate_json(raw)
    except Exception:
        raise HTTPException(422, "Payload thanh toán không hợp lệ")
    with connection() as db:
        db.execute("BEGIN IMMEDIATE")
        try:
            prior = db.execute("SELECT event_id FROM payment_events WHERE event_id=?",
                               (event.event_id,)).fetchone()
            if prior:
                db.execute("COMMIT")
                return {"accepted":True, "duplicate":True}
            row = db.execute("SELECT * FROM bookings WHERE payment_reference=?",
                             (event.reference,)).fetchone()
            if row is None:
                raise HTTPException(404, "Không có giao dịch chờ khớp")
            if event.amount != row["amount"]:
                raise HTTPException(409, "Sai số tiền so với đơn hàng")
            if row["payment_status"] == "PAID" and event.status == "SUCCESS":
                db.execute("COMMIT")
                return {"accepted": True, "duplicate": True}
            if row["payment_status"] != "BANK_PENDING" or row["status"] != "CONFIRMED":
                raise HTTPException(409, "Trạng thái thanh toán không hợp lệ")
            db.execute("INSERT INTO payment_events(event_id,booking_id,provider_reference,amount,event_type,created_at) "
                       "VALUES(?,?,?,?,?,?)", (event.event_id, row["id"],event.reference,
                       event.amount,event.status,iso_now()))
            if event.status == "SUCCESS":
                db.execute("UPDATE bookings SET payment_status='PAID',paid_at=?,updated_at=? WHERE id=?",
                           (iso_now(),iso_now(),row["id"]))
            db.execute("COMMIT")
            return {"accepted":True,"duplicate":False}
        except Exception:
            db.execute("ROLLBACK")
            raise
