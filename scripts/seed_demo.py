"""Seed harmless fictional CourtMate Cloud demo records. Run locally only."""
from __future__ import annotations

import os
import sys
import secrets
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))

from app import init_db, connection  # noqa: E402


def main() -> None:
    path = os.environ.get("COURTMATE_DB", "")
    if not path or "demo" not in Path(path).name.lower():
        raise SystemExit("Chỉ nạp vào COURTMATE_DB có tên chứa 'demo'.")
    init_db()
    courts = [("Sân Pickleball A", 120000), ("Sân Pickleball B", 150000),
              ("Sân Premium C", 200000), ("Sân Tập D", 90000)]
    d = (datetime.now(ZoneInfo("Asia/Ho_Chi_Minh")) + timedelta(days=1)).date().isoformat()
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")
    samples = [("Sân Pickleball A", "08:00", "09:30"),
               ("Sân Pickleball A", "14:00", "15:30"),
               ("Sân Pickleball B", "16:00", "17:30"),
               ("Sân Premium C", "18:00", "19:30")]
    with connection() as db:
        with db:
            for name, price in courts:
                db.execute("INSERT OR IGNORE INTO courts(name,price_hour,active) VALUES (?,?,1)",
                           (name, price))
            for name, start, end in samples:
                row = db.execute("SELECT id,price_hour FROM courts WHERE name=?", (name,)).fetchone()
                already = db.execute("SELECT id FROM bookings WHERE court_id=? AND date=? AND start=? AND end=?",
                                     (row['id'], d, start, end)).fetchone()
                if already:
                    continue
                price = int(row['price_hour'] * 1.5)
                db.execute("""INSERT INTO bookings(court_id,date,start,end,customer_name,customer_phone,
                      amount,status,payment_status,public_token,payment_reference,payment_url,paid_at,
                      created_at,hold_until,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                      (row['id'],d,start,end,"Khách demo", "0900000000", price,
                       "CONFIRMED", "UNPAID", secrets.token_urlsafe(32), None,None,None,
                       now,(datetime.now(timezone.utc) + timedelta(days=2)).isoformat(),now))
    print("Đã có 4 sân demo và lịch đặt minh họa cho ngày", d)

if __name__ == "__main__":
    main()
