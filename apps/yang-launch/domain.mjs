import { object, text, choice, requireThat, dayAfter } from '../../lib/validation.mjs';
import { date } from '../../lib/validation.mjs';
export const STAGES = ['idea', 'draft', 'review', 'ready', 'published'];
export const TRANSITIONS = {
  idea: ['draft'],
  draft: ['review'],
  review: ['draft', 'ready'],
  ready: ['review', 'published'],
  published: ['draft'],
};
// Port of json_to_typefully_content from the MIT upstream scheduler.
export function formatThread(thread) {
  return thread.tweets
    .map((tweet) => tweet.content + (tweet.media_urls?.length ? '\n' + tweet.media_urls[0] : ''))
    .join('\n\n\n\n');
}
export function validate(input, existing = null) {
  object(input);
  const stage = choice(input.stage ?? 'idea', STAGES, 'Giai đoạn');
  if (!existing)
    requireThat(['idea', 'draft'].includes(stage), 'Bài mới cần bắt đầu ở Ý tưởng hoặc Bản nháp.');
  else if (stage !== existing.stage)
    requireThat(
      TRANSITIONS[existing.stage].includes(stage),
      'Không thể chuyển trực tiếp từ ' + existing.stage + ' sang ' + stage + '.',
      409,
    );
  const caption = text(input.caption, 'Nội dung', 12000, false);
  if (stage !== 'idea')
    requireThat(caption.length >= 10, 'Bản nháp cần ít nhất 10 ký tự nội dung.');
  return {
    title: text(input.title, 'Tiêu đề', 120),
    campaign: text(input.campaign, 'Chiến dịch', 80, false),
    channel: choice(input.channel, ['facebook', 'linkedin', 'blog', 'x'], 'Kênh'),
    due: date(input.due, 'Lịch dự kiến', true),
    owner: text(input.owner, 'Người phụ trách', 60, false),
    stage,
    caption,
    notes: text(input.notes, 'Ghi chú', 4000, false),
  };
}
export function csv(records) {
  // Prevent spreadsheet formula execution while retaining CSV quoting and UTF-8.
  const cell = (value) =>
    '"' +
    (/^[=+\-@\t\r]/.test(String(value)) ? "'" : '') +
    String(value).replaceAll('"', '""') +
    '"';
  const rows = [
    ['Tiêu đề', 'Chiến dịch', 'Kênh', 'Ngày dự kiến', 'Người phụ trách', 'Giai đoạn', 'Nội dung'],
  ];
  for (const { data: d } of records)
    rows.push([d.title, d.campaign, d.channel, d.due, d.owner, d.stage, d.caption]);
  return '\ufeff' + rows.map((row) => row.map(cell).join(',')).join('\r\n') + '\r\n';
}
export function markdown(record) {
  const d = record.data;
  const caption =
    d.channel === 'x'
      ? formatThread({
          tweets: d.caption.split(/\n---\n/).map((content) => ({ content: content.trim() })),
        })
      : d.caption;
  return [
    '# ' + d.title,
    '',
    'Kênh: ' + d.channel,
    'Chiến dịch: ' + d.campaign,
    'Lịch dự kiến: ' + (d.due || 'Chưa xếp lịch'),
    '',
    caption,
    '',
    '## Ghi chú',
    '',
    d.notes,
    '',
    'Đây là kế hoạch. YangLaunch không tự đăng lên mạng xã hội.',
    '',
  ].join('\n');
}
export function demo(day) {
  return [
    {
      title: 'Một buổi sáng đi xe buýt',
      campaign: 'Đi lại xanh',
      channel: 'facebook',
      due: dayAfter(day, 1),
      owner: 'Giang',
      stage: 'draft',
      caption: 'Chia sẻ một lựa chọn đi lại xanh và ba điều cần chuẩn bị trước chuyến đi.',
      notes: 'Nội dung minh họa. Kiểm chứng thông tin trước khi đăng.',
    },
    {
      title: 'Góc học tập không rác nhựa',
      campaign: 'Sống gọn',
      channel: 'blog',
      due: dayAfter(day, 3),
      owner: 'Linh',
      stage: 'idea',
      caption: '',
      notes: 'Phác thảo: đồ dùng tái sử dụng, góc phân loại và thói quen nhỏ.',
    },
    {
      title: 'Hành trình xây một API nhỏ',
      campaign: 'Nhật ký lập trình',
      channel: 'linkedin',
      due: dayAfter(day, 5),
      owner: 'Giang',
      stage: 'draft',
      caption:
        'Từ một endpoint đến một luồng có kiểm thử: ghi lại bài học về validation, phân quyền và tài liệu API.',
      notes: 'Dữ liệu mẫu, không phải thành tích đã được xác nhận.',
    },
  ];
}
