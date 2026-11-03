import { createHmac, timingSafeEqual } from 'node:crypto';
import { check } from './validation.mjs';
export function sign(secret, timestamp, delivery, raw) {
  return (
    'sha256=' +
    createHmac('sha256', secret)
      .update(String(timestamp) + '.' + delivery + '.')
      .update(raw)
      .digest('hex')
  );
}
export function verify(secret, headers, raw, now = Date.now()) {
  const timestamp = headers['x-yang-timestamp'],
    delivery = headers['x-yang-delivery'],
    signature = headers['x-yang-signature'];
  const valid =
    typeof timestamp === 'string' &&
    /^\d{10}$/.test(timestamp) &&
    typeof delivery === 'string' &&
    /^[a-zA-Z0-9_-]{8,100}$/.test(delivery) &&
    typeof signature === 'string' &&
    /^sha256=[a-f0-9]{64}$/.test(signature);
  check(valid, 'Chữ ký, delivery ID hoặc thời gian không hợp lệ.', 401);
  check(
    Math.abs(Math.floor(now / 1000) - Number(timestamp)) <= 300,
    'Chữ ký, delivery ID hoặc thời gian không hợp lệ.',
    401,
  );
  const expected = Buffer.from(sign(secret, timestamp, delivery, raw).slice(7), 'hex'),
    actual = Buffer.from(signature.slice(7), 'hex');
  check(timingSafeEqual(expected, actual), 'Chữ ký, delivery ID hoặc thời gian không hợp lệ.', 401);
  return { timestamp: Number(timestamp), delivery };
}
export function safeHeaders(headers) {
  const output = {};
  for (const name of ['content-type', 'user-agent', 'x-yang-timestamp', 'x-yang-delivery'])
    if (typeof headers[name] === 'string') output[name] = headers[name].slice(0, 200);
  return output;
}
