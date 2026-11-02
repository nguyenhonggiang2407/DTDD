// Documentation title/description/examples planning is adapted from MIT DocPlan.
import { object, text, list, id, requireThat, HttpError } from '../../lib/validation.mjs';
export const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'];
function sample(raw, label) {
  if (raw == null || raw === '') return null;
  const source = text(raw, label, 12000);
  let parsed;
  try {
    parsed = JSON.parse(source);
  } catch {
    throw new HttpError(400, label + ' không phải JSON hợp lệ.');
  }
  let nodes = 0;
  function inspect(value, depth) {
    requireThat(depth <= 7 && ++nodes <= 1500, label + ' quá phức tạp.');
    if (Array.isArray(value)) value.forEach((v) => inspect(v, depth + 1));
    else if (value && typeof value === 'object')
      Object.values(value).forEach((v) => inspect(v, depth + 1));
  }
  inspect(parsed, 0);
  return source;
}
export function infer(value) {
  if (value === null) return { type: 'null' };
  if (Array.isArray(value)) {
    const schemas = [
      ...new Map(
        value.map((v) => {
          const s = infer(v);
          return [JSON.stringify(s), s];
        }),
      ).values(),
    ];
    return {
      type: 'array',
      items: schemas.length === 1 ? schemas[0] : schemas.length ? { anyOf: schemas } : {},
    };
  }
  if (typeof value === 'object') {
    const properties = Object.fromEntries(
      Object.entries(value).map(([key, val]) => [key, infer(val)]),
    );
    return { type: 'object', properties };
  }
  return {
    type:
      typeof value === 'number' ? (Number.isInteger(value) ? 'integer' : 'number') : typeof value,
  };
}
export function validate(input) {
  object(input);
  const release = text(input.release ?? '1.0.0', 'Phiên bản API', 30);
  requireThat(/^[a-zA-Z0-9.+-]+$/.test(release), 'Phiên bản API không hợp lệ.');
  const endpoints = list(input.endpoints ?? [], 40, 'Endpoint').map((raw) => {
    object(raw);
    const method = text(raw.method, 'HTTP method', 10).toUpperCase();
    requireThat(METHODS.includes(method), 'HTTP method không được hỗ trợ.');
    const path = text(raw.path, 'Đường dẫn', 180);
    requireThat(
      /^\/[a-zA-Z0-9/{}_.~-]*$/.test(path) && !path.includes('//'),
      'Đường dẫn cần bắt đầu bằng /, không chứa query hoặc ký tự đặc biệt.',
    );
    const parameters = [...path.matchAll(/\{([a-zA-Z][a-zA-Z0-9_]*)\}/g)].map((m) => m[1]);
    requireThat(
      !/[{}]/.test(path.replace(/\{[a-zA-Z][a-zA-Z0-9_]*\}/g, '')),
      'Tên path parameter không hợp lệ.',
    );
    requireThat(new Set(parameters).size === parameters.length, 'Path parameter bị trùng.');
    const status = Number(raw.status ?? 200);
    requireThat(
      Number.isInteger(status) && status >= 100 && status <= 599,
      'Mã phản hồi cần từ 100 đến 599.',
    );
    const request = sample(raw.request, 'JSON request');
    const response = sample(raw.response, 'JSON response');
    requireThat(
      !['GET', 'HEAD'].includes(method) || request === null,
      'GET/HEAD không nhận request body trong trình thiết kế này.',
    );
    requireThat(
      ![204, 304].includes(status) || response === null,
      'Phản hồi 204/304 không có body.',
    );
    return {
      id: id(raw.id),
      method,
      path,
      summary: text(raw.summary, 'Tóm tắt endpoint', 180),
      status,
      request,
      response,
    };
  });
  requireThat(
    new Set(endpoints.map((e) => e.method + ' ' + e.path)).size === endpoints.length,
    'Trùng method và đường dẫn.',
  );
  requireThat(
    new Set(endpoints.map((e) => e.id)).size === endpoints.length,
    'ID endpoint bị trùng.',
  );
  const shapes = new Map();
  for (const endpoint of endpoints) {
    const shape = endpoint.path.replace(/\{[^}]+\}/g, '{}');
    requireThat(
      !shapes.has(shape) || shapes.get(shape) === endpoint.path,
      'Hai đường dẫn trùng cấu trúc nhưng khác tên path parameter.',
    );
    shapes.set(shape, endpoint.path);
  }
  return {
    title: text(input.title, 'Tên API', 120),
    release,
    description: text(input.description, 'Mô tả', 4000, false),
    endpoints,
  };
}
export function openapi(record) {
  const d = record.data;
  const paths = Object.create(null);
  for (const e of d.endpoints) {
    const operation = {
      operationId: e.method.toLowerCase() + '_' + e.id.replaceAll('-', '_'),
      summary: e.summary,
      responses: {
        [e.status]: { description: e.status < 400 ? 'Phản hồi thành công' : 'Phản hồi lỗi' },
      },
    };
    const parameters = [...e.path.matchAll(/\{([^}]+)\}/g)].map((m) => ({
      name: m[1],
      in: 'path',
      required: true,
      schema: { type: 'string' },
    }));
    if (parameters.length) operation.parameters = parameters;
    if (e.request !== null)
      operation.requestBody = {
        required: true,
        content: {
          'application/json': {
            schema: infer(JSON.parse(e.request)),
            example: JSON.parse(e.request),
          },
        },
      };
    if (e.response !== null)
      operation.responses[e.status].content = {
        'application/json': {
          schema: infer(JSON.parse(e.response)),
          example: JSON.parse(e.response),
        },
      };
    paths[e.path] ??= Object.create(null);
    paths[e.path][e.method.toLowerCase()] = operation;
  }
  return {
    openapi: '3.1.0',
    info: { title: d.title, version: d.release, description: d.description },
    paths,
  };
}
export function markdown(record) {
  const d = record.data,
    fence = String.fromCharCode(96).repeat(3);
  const lines = ['# ' + d.title, '', 'Phiên bản: ' + d.release, '', d.description, ''];
  for (const e of d.endpoints) {
    lines.push('## ' + e.method + ' ' + e.path, '', e.summary, '', 'Mã phản hồi: ' + e.status, '');
    for (const [label, value] of [
      ['Request', e.request],
      ['Response', e.response],
    ]) {
      if (value !== null)
        lines.push(
          '### ' + label,
          '',
          fence + 'json',
          JSON.stringify(JSON.parse(value), null, 2),
          fence,
          '',
        );
    }
  }
  lines.push(
    'Schema được suy ra từ ví dụ. Hãy rà soát ràng buộc trước khi dùng làm hợp đồng triển khai.',
    '',
  );
  return lines.join('\n');
}
export function demo() {
  return [
    {
      title: 'Library API',
      release: '1.0.0',
      description:
        'Hợp đồng API minh họa cho một thư viện tài liệu. Không phải API đang được triển khai.',
      endpoints: [
        {
          id: id(),
          method: 'GET',
          path: '/documents/{id}',
          summary: 'Đọc một tài liệu theo ID',
          status: 200,
          request: null,
          response: '{"id":"doc-01","title":"Backend notes","pages":3}',
        },
        {
          id: id(),
          method: 'POST',
          path: '/documents',
          summary: 'Thêm tài liệu mới',
          status: 201,
          request: '{"title":"Backend notes"}',
          response: '{"id":"doc-02","title":"Backend notes"}',
        },
      ],
    },
  ];
}
