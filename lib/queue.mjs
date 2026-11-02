import { Worker } from 'node:worker_threads';
function spawn(job) {
  return new Worker(new URL('./processor-worker.mjs', import.meta.url), {
    workerData: { type: job.type, input: job.input, options: job.options },
    env: { NODE_ENV: process.env.NODE_ENV || 'production' },
    resourceLimits: { maxOldGenerationSizeMb: 64, maxYoungGenerationSizeMb: 16, stackSizeMb: 2 },
  });
}
export class Queue {
  constructor(store, options = {}) {
    this.store = store; this.concurrency = options.concurrency || 2;
    this.timeoutMs = options.timeoutMs || 5000; this.intervalMs = options.intervalMs || 250;
    this.spawn = options.spawn || spawn; this.running = new Map(); this.closed = false;
  }
  start() { this.closed = false; this.tick(); this.timer = setInterval(() => this.tick(), this.intervalMs); this.timer.unref(); }
  tick() {
    if (this.closed) return;
    this.store.recoverExpired();
    while (this.running.size < this.concurrency) {
      const job = this.store.claim(); if (!job) break; this.execute(job);
    }
  }
  execute(job) {
    let worker;
    try { worker = this.spawn(job); }
    catch { this.store.fail(job.id, job.token, { code: 'WORKER_START', message: 'Không khởi động được worker.', retryable: true }); return; }
    const entry = { worker, job, settled: false, timeout: null, heartbeat: null };
    this.running.set(job.id, entry);
    const finish = (kind, value) => {
      if (entry.settled) return;
      entry.settled = true; clearTimeout(entry.timeout); clearInterval(entry.heartbeat); this.running.delete(job.id);
      if (!this.closed) {
        if (kind === 'result') {
          try { this.store.complete(job.id, job.token, value); }
          catch { this.store.fail(job.id, job.token, { code: 'RESULT_INVALID', message: 'Đầu ra worker không vượt qua kiểm tra toàn vẹn.', retryable: false }); }
        } else this.store.fail(job.id, job.token, value);
      }
      worker.terminate().catch(() => {});
    };
    worker.on('message', message => {
      if (entry.settled || this.closed) return;
      if (message.kind === 'stage') this.store.phase(job.id, job.token, message.label);
      if (message.kind === 'result') finish('result', message.output);
      if (message.kind === 'failure') finish('failure', message.error);
    });
    worker.on('error', () => finish('failure', { code: 'WORKER_ERROR', message: 'Worker kết thúc trước khi có kết quả.', retryable: true }));
    worker.on('exit', () => { if (!entry.settled) finish('failure', { code: 'WORKER_EXIT', message: 'Worker đã dừng trước khi trả kết quả.', retryable: true }); });
    entry.timeout = setTimeout(() => finish('failure', { code: 'JOB_TIMEOUT', message: 'Tác vụ vượt giới hạn thời gian xử lý.', retryable: true }), this.timeoutMs);
    entry.heartbeat = setInterval(() => { if (!entry.settled && !this.closed && !this.store.heartbeat(job.id, job.token)) this.cancel(job.id); }, 1000);
    entry.timeout.unref(); entry.heartbeat.unref();
  }
  cancel(id) {
    const entry = this.running.get(id); if (!entry) return;
    entry.settled = true; clearTimeout(entry.timeout); clearInterval(entry.heartbeat);
    this.running.delete(id); entry.worker.terminate().catch(() => {});
  }
  async close() {
    this.closed = true; clearInterval(this.timer); const entries = [...this.running.values()];
    for (const e of entries) { e.settled = true; clearTimeout(e.timeout); clearInterval(e.heartbeat); }
    await Promise.allSettled(entries.map(e => e.worker.terminate()));
    for (const e of entries) this.store.fail(e.job.id, e.job.token, { code: 'WORKER_STOPPED', message: 'Worker dừng cùng máy chủ; tác vụ sẽ được thử lại.', retryable: true });
    this.running.clear();
  }
}
