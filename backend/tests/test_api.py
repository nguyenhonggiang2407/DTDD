import hashlib
import hmac
import importlib
import json
import os
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import pytest
from fastapi.testclient import TestClient

@pytest.fixture
def api(tmp_path, monkeypatch):
    monkeypatch.setenv("COURTMATE_DB", str(tmp_path / "court.db"))
    monkeypatch.setenv("COURTMATE_ADMIN_TOKEN", "test-admin-token-longer-than-thirty-two-characters")
    monkeypatch.setenv("COURTMATE_GATEWAY_WEBHOOK_SECRET", "test-gateway-secret-longer-than-thirty-two-characters")
    monkeypatch.setenv("COURTMATE_PAYMENT_URL_TEMPLATE", "https://payments.example.invalid/pay?r={reference}&v={amount}")
    monkeypatch.setenv("COURTMATE_PUBLIC_BOOKING", "true")
    import app
    app.init_db()
    app._public_attempts.clear()
    with TestClient(app.app) as c:
        yield c, app


def admin():
    return {"Authorization":"Bearer test-admin-token-longer-than-thirty-two-characters"}


def book(date, start="18:00", end="19:00"):
    return {"court_id":1,"date":date,"start":start,"end":end,
            "customer_name":"Nguyen Van A","customer_phone":"0987654321"}


def today():
    # Book tomorrow so tests do not fail after 18:00 local time.
    from datetime import datetime, timedelta
    from zoneinfo import ZoneInfo
    return (datetime.now(ZoneInfo('Asia/Ho_Chi_Minh')).date() + timedelta(days=1)).isoformat()


def test_rbac_and_booking_conflict(api):
    c, _ = api
    assert c.get('/v1/admin/bookings').status_code == 401
    r = c.post('/v1/admin/courts', headers=admin(),json={"name":"San 1","price_hour":120000})
    assert r.status_code == 201
    b = c.post('/v1/public/bookings',json=book(today()))
    assert b.status_code == 201
    assert b.json()['amount'] == 120000
    assert b.json()['status'] == 'PENDING'
    assert 'customer_phone' not in b.json()
    assert c.post('/v1/public/bookings',json=book(today(),"18:30","19:30")).status_code == 409
    assert c.post('/v1/public/bookings',json=book(today(),"19:00","20:00")).status_code == 201
    listing = c.get('/v1/admin/bookings', headers=admin()).json()
    assert len(listing) == 2
    assert c.patch(f"/v1/admin/bookings/{b.json()['id']}",headers=admin(),json={"status":"CONFIRMED"}).status_code == 200


def test_concurrent_reservation_one_winner(api):
    c, application = api
    c.post('/v1/admin/courts',headers=admin(),json={"name":"San A","price_hour":100000})
    barrier = threading.Barrier(6)
    def reserve(_):
        barrier.wait()
        return c.post('/v1/public/bookings',json=book(today())).status_code
    with ThreadPoolExecutor(max_workers=6) as pool:
        statuses = list(pool.map(reserve, range(6)))
    assert statuses.count(201)==1, statuses
    assert statuses.count(409)==5, statuses


def test_payment_rejects_unverified_and_duplicate(api):
    c, application = api
    c.post('/v1/admin/courts', headers=admin(),json={"name":"San A","price_hour":100000})
    booking = c.post('/v1/admin/bookings',headers=admin(),json=book(today())).json()
    booking_id = booking['id']
    intent = c.post(f'/v1/admin/bookings/{booking_id}/bank-intent',headers=admin())
    assert intent.status_code == 200
    data = intent.json()
    event = {"event_id":"evt-0000001","reference":data['reference'],"amount":100000,
             "currency":"VND","status":"SUCCESS"}
    body = json.dumps(event,separators=(',',':')).encode()
    timestamp = str(int(time.time()))
    sig = hmac.new(os.getenv("COURTMATE_GATEWAY_WEBHOOK_SECRET").encode(),
                   timestamp.encode()+b'.'+body,hashlib.sha256).hexdigest()
    headers={'X-Courtmate-Timestamp':timestamp,'X-Courtmate-Signature':sig,'Content-Type':'application/json'}
    assert c.post('/v1/provider/webhooks/payment',content=body,headers={**headers,'X-Courtmate-Signature':'wrong'}).status_code==401
    wrong = dict(event, event_id='evt-0000002', amount=99999)
    wrong_body = json.dumps(wrong,separators=(',',':')).encode()
    wrong_sig = hmac.new(os.getenv('COURTMATE_GATEWAY_WEBHOOK_SECRET').encode(),
                         timestamp.encode()+b'.'+wrong_body,hashlib.sha256).hexdigest()
    assert c.post('/v1/provider/webhooks/payment',content=wrong_body,
                  headers={**headers,'X-Courtmate-Signature':wrong_sig}).status_code==409
    assert c.post('/v1/provider/webhooks/payment',content=body,headers=headers).json()['accepted'] is True
    assert c.post('/v1/provider/webhooks/payment',content=body,headers=headers).json()['duplicate'] is True
    assert c.get('/v1/admin/bookings',headers=admin()).json()[0]['payment_status'] == 'PAID'
    assert c.patch(f'/v1/admin/bookings/{booking_id}',headers=admin(),json={"status":"CANCELLED"}).status_code==409
    assert c.post(f'/v1/admin/bookings/{booking_id}/bank-intent',headers=admin()).status_code==409


def test_public_portal_off_and_invalid_duration(api, monkeypatch):
    c,_ = api
    c.post('/v1/admin/courts', headers=admin(),json={"name":"San 1","price_hour":60000})
    assert c.post('/v1/public/bookings',json=book(today(),"18:00","18:10")).status_code==422
    monkeypatch.setenv('COURTMATE_PUBLIC_BOOKING','false')
    assert c.post('/v1/public/bookings',json=book(today())).status_code==503
    assert c.get('/v1/public/courts').status_code==503


def test_customer_private_status_and_payment_url(api):
    c, _ = api
    c.post('/v1/admin/courts',headers=admin(),json={"name":"San 1","price_hour":100000})
    created = c.post('/v1/public/bookings',json=book(today())).json()
    assert created['access_token']
    payload = {'id':created['id'], 'access_token':'bad_bad_bad_bad_bad_bad_bad_bad_bad_bad_bad'}
    assert c.post('/v1/public/booking-status',json=payload).status_code == 404
    payload['access_token'] = created['access_token']
    status = c.post('/v1/public/booking-status',json=payload).json()
    assert status['status'] == 'PENDING'
    assert 'customer_name' not in status and 'customer_phone' not in status
    c.post(f"/v1/admin/bookings/{created['id']}/status",headers=admin(),json={'status':'CONFIRMED'})
    c.post(f"/v1/admin/bookings/{created['id']}/bank-intent",headers=admin())
    status = c.post('/v1/public/booking-status',json=payload).json()
    assert status['payment_status'] == 'BANK_PENDING'
    assert status['payment_url'].startswith('https://')
    assert c.post(f"/v1/admin/bookings/{created['id']}/status",headers=admin(),
                  json={'status':'CANCELLED'}).status_code == 409


def test_expired_hold_is_released(api):
    c, application = api
    c.post('/v1/admin/courts',headers=admin(),json={"name":"San 1","price_hour":100000})
    created = c.post('/v1/public/bookings',json=book(today())).json()
    with application.connection() as db:
        db.execute("UPDATE bookings SET hold_until='2000-01-01T00:00:00+00:00' WHERE id=?", (created['id'],))
    new = c.post('/v1/public/bookings',json=book(today()))
    assert new.status_code == 201
    with application.connection() as db:
        row = db.execute('SELECT status FROM bookings WHERE id=?',(created['id'],)).fetchone()
        assert row['status'] == 'CANCELLED'


def test_replay_timestamp_rejected(api):
    c, application = api
    body = b'{"event_id":"deadbeef","reference":"CM123","amount":1000,"currency":"VND","status":"SUCCESS"}'
    time_sent = str(int(time.time()) - 1000)
    sig = hmac.new(os.environ['COURTMATE_GATEWAY_WEBHOOK_SECRET'].encode(),time_sent.encode()+b'.'+body,hashlib.sha256).hexdigest()
    response = c.post('/v1/provider/webhooks/payment',content=body, headers={
        'X-Courtmate-Timestamp':time_sent,'X-Courtmate-Signature':sig})
    assert response.status_code == 401


def test_payment_disabled_without_partner_env(api,monkeypatch):
    c, application = api
    c.post('/v1/admin/courts',headers=admin(),json={'name':'San 1','price_hour':120000})
    record = c.post('/v1/admin/bookings',headers=admin(),json=book(today())).json()
    monkeypatch.delenv('COURTMATE_PAYMENT_URL_TEMPLATE',raising=False)
    r=c.post(f"/v1/admin/bookings/{record['id']}/bank-intent",headers=admin())
    assert r.status_code==503
    assert c.get('/v1/admin/bookings',headers=admin()).json()[0]['payment_status']=='UNPAID'


def test_public_cancel_requires_secret_and_releases_slot(api):
    c, _ = api
    c.post('/v1/admin/courts', headers=admin(), json={"name":"San A", "price_hour":120000})
    created = c.post('/v1/public/bookings', json=book(today())).json()
    data = {"id": created['id'], "access_token": created['access_token']}
    assert c.post('/v1/public/bookings/cancel', json={**data, 'access_token':'wrong-secret-' * 3}).status_code == 404
    cancelled = c.post('/v1/public/bookings/cancel', json=data)
    assert cancelled.status_code == 200
    assert cancelled.json()['status'] == 'CANCELLED'
    assert c.post('/v1/public/bookings/cancel', json=data).status_code == 200  # idempotent
    assert c.post('/v1/public/bookings', json=book(today())).status_code == 201


def test_confirmed_booking_cannot_be_cancelled_by_customer(api):
    c, _ = api
    c.post('/v1/admin/courts', headers=admin(), json={"name":"San A", "price_hour":120000})
    created = c.post('/v1/public/bookings', json=book(today())).json()
    c.patch(f"/v1/admin/bookings/{created['id']}", headers=admin(), json={'status':'CONFIRMED'})
    assert c.post('/v1/public/bookings/cancel', json={
        'id':created['id'], 'access_token':created['access_token']}).status_code == 409


def test_court_maintenance_via_admin_and_android_alias(api):
    c, _ = api
    assert c.patch('/v1/admin/courts/1', json={'active':False}).status_code == 401
    created = c.post('/v1/admin/courts', headers=admin(), json={
        'name':'San A', 'price_hour':100000}).json()
    court_id = created['id']
    assert c.patch(f'/v1/admin/courts/{court_id}', headers=admin(), json={}).status_code == 422
    assert c.patch(f'/v1/admin/courts/{court_id}', headers=admin(), json={'name':'   '}).status_code == 422
    disabled = c.post(f'/v1/admin/courts/{court_id}/update', headers=admin(), json={
        'active':False, 'price_hour':140000})
    assert disabled.status_code == 200
    assert disabled.json()['active'] == 0
    assert disabled.json()['price_hour'] == 140000
    assert c.post('/v1/public/bookings', json=book(today())).status_code == 404
    assert c.get('/v1/public/availability', params={'court_id':court_id,'date':today()}).status_code == 404
    assert c.patch(f'/v1/admin/courts/{court_id}', headers=admin(), json={'active':True}).status_code == 200
    assert c.post('/v1/public/bookings', json=book(today())).status_code == 201


def test_summary_daily_counts_and_actual_paid_amount(api):
    c, application = api
    assert c.get('/v1/admin/summary').status_code == 401
    c.post('/v1/admin/courts', headers=admin(), json={"name":"San A", "price_hour":100000})
    pending = c.post('/v1/public/bookings', json=book(today())).json()
    assert c.patch(f"/v1/admin/bookings/{pending['id']}",headers=admin(),json={'status':'CONFIRMED'}).status_code==200
    c.post('/v1/admin/bookings',headers=admin(),json=book(today(),'19:00','20:00'))
    c.post('/v1/public/bookings',json=book(today(),'20:00','21:00'))
    cancelled = c.post('/v1/public/bookings',json=book(today(),'21:00','22:00')).json()
    c.post('/v1/public/bookings/cancel',json={'id':cancelled['id'],'access_token':cancelled['access_token']})
    with application.connection() as db:
        db.execute("UPDATE bookings SET payment_status='PAID' WHERE id=?", (pending['id'],))
    data = c.get('/v1/admin/summary',headers=admin(),params={'date':today()}).json()
    assert data['active_courts'] == 1
    assert data['total_bookings'] == 4
    assert data['confirmed'] == 2 and data['pending'] == 1 and data['cancelled'] == 1
    assert data['paid_amount'] == 100000 and data['amount_to_collect'] == 100000
    assert c.get('/v1/admin/summary',headers=admin(),params={'date':'bad'}).status_code == 422


def test_staff_api_does_not_expose_customer_secret(api):
    c, _ = api
    c.post('/v1/admin/courts',headers=admin(),json={'name':'San A','price_hour':100000})
    created = c.post('/v1/public/bookings',json=book(today())).json()
    assert len(created['access_token']) >= 30
    listing = c.get('/v1/admin/bookings',headers=admin()).json()
    assert 'public_token' not in listing[0]
    internal = c.post('/v1/admin/bookings',headers=admin(),json=book(today(),'19:00','20:00')).json()
    assert 'public_token' not in internal
    assert c.get('/v1/admin/bookings',headers=admin(),params={'date':'broken-date'}).status_code == 422


def test_today_past_times_are_blocked(api):
    c, _ = api
    from datetime import datetime
    from zoneinfo import ZoneInfo
    c.post('/v1/admin/courts',headers=admin(),json={'name':'San A','price_hour':100000})
    date = datetime.now(ZoneInfo('Asia/Ho_Chi_Minh')).date().isoformat()
    past = c.post('/v1/public/bookings',json=book(date,'00:00','01:00'))
    assert past.status_code == 422


def test_public_status_is_rate_limited(api):
    c, _ = api
    payload = {'id':1, 'access_token':'a' * 32}
    statuses = [c.post('/v1/public/booking-status',json=payload).status_code for _ in range(31)]
    assert statuses[:30] == [404]*30
    assert statuses[-1] == 429


def test_court_name_is_normalized(api):
    c, _ = api
    assert c.post('/v1/admin/courts',headers=admin(),json={'name':'  ', 'price_hour':100}).status_code == 422
    record = c.post('/v1/admin/courts',headers=admin(),json={'name':'  San A  ','price_hour':100}).json()
    assert record['name'] == 'San A'
    assert c.post('/v1/admin/courts',headers=admin(),json={'name':'San A','price_hour':100}).status_code == 409
