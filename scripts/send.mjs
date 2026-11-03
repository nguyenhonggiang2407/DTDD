import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { sign } from '../lib/signature.mjs';
const [url, file, deliveryId = randomUUID()] = process.argv.slice(2),
  secret = process.env.YANGHOOKS_SECRET;
if (!url || !file || !secret) {
  console.error(
    'Usage: YANGHOOKS_SECRET=... node scripts/send.mjs http://127.0.0.1:3140/hooks/ID payload.json [delivery-id]',
  );
  process.exit(1);
}
const target = new URL(url);
if (
  !['http:', 'https:'].includes(target.protocol) ||
  !['localhost', '127.0.0.1'].includes(target.hostname) ||
  !/^\/hooks\/[a-f0-9-]{36}$/.test(target.pathname) ||
  target.username ||
  target.password
) {
  console.error('This local lab CLI only sends to localhost or 127.0.0.1 /hooks/ID.');
  process.exit(1);
}
const raw = readFileSync(file);
if (raw.length > 65536) {
  console.error('Body is larger than 64 KiB.');
  process.exit(1);
}
const timestamp = String(Math.floor(Date.now() / 1000));
try {
  const response = await fetch(target, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Yang-Timestamp': timestamp,
      'X-Yang-Delivery': deliveryId,
      'X-Yang-Signature': sign(secret, timestamp, deliveryId, raw),
    },
    body: raw,
    signal: AbortSignal.timeout(5000),
    redirect: 'error',
  });
  console.log(response.status, await response.text());
  if (!response.ok) process.exitCode = 1;
} catch {
  console.error('Could not deliver the local request.');
  process.exitCode = 1;
}
