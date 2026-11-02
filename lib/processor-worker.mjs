import { parentPort, workerData } from 'node:worker_threads';
import { processJob, InputError } from './processors.mjs';
try {
  const output = processJob(workerData, (label) =>
    parentPort.postMessage({ kind: 'stage', label }),
  );
  parentPort.postMessage({ kind: 'result', output });
} catch (err) {
  parentPort.postMessage({
    kind: 'failure',
    error: {
      code: err instanceof InputError ? err.code : 'WORKER_ERROR',
      message: err instanceof InputError ? err.message : 'Worker không thể hoàn tất tác vụ.',
      retryable: !(err instanceof InputError),
    },
  });
}
