import { HttpError, check, object } from './validation.mjs';
export function send(res, body, status = 200) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(body));
}
export function readRaw(req, max = 65536) {
  check(!req.headers['content-encoding'], 'Không nhận body đã nén.', 415);
  check(
    (req.headers['content-type'] || '').split(';')[0].trim() === 'application/json',
    'Cần application/json.',
    415,
  );
  check(Number(req.headers['content-length'] || 0) <= max, 'Body vượt quá giới hạn.', 413);
  return new Promise((resolve, reject) => {
    let bytes = 0,
      failed = false,
      chunks = [];
    req.on('data', (chunk) => {
      bytes += chunk.length;
      if (bytes > max && !failed) {
        failed = true;
        chunks = [];
        reject(new HttpError(413, 'Body vượt quá giới hạn.'));
      } else if (!failed) chunks.push(chunk);
    });
    req.once('end', () => {
      if (!failed) resolve(Buffer.concat(chunks));
    });
    req.once('error', reject);
    req.once('aborted', () => reject(new HttpError(400, 'Request đã bị ngắt.')));
  });
}
export async function readJSON(req) {
  const raw = await readRaw(req, 262144);
  try {
    return object(JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(raw)));
  } catch (error) {
    if (error instanceof HttpError) throw error;
    throw new HttpError(400, 'JSON UTF-8 không hợp lệ.');
  }
}
