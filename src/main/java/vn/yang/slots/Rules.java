package vn.yang.slots;

import static vn.yang.slots.Problem.require;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.*;
import java.util.*;

final class Rules {
  static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  static final long MINUTE = 60000L, HOLD_TTL = 300000L, SESSION_TTL = 7 * 86400000L;
  private static final SecureRandom RANDOM = new SecureRandom();

  record Window(long start, long end, LocalDate day) {}

  record Span(long start, long end, int units) {}

  static String text(String value, String label, int max) {
    require(value != null, 400, label + " không hợp lệ.");
    String clean = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    require(
        !clean.isEmpty()
            && clean.length() <= max
            && clean
                .codePoints()
                .noneMatch(cp -> Character.isISOControl(cp) || (cp >= 0xD800 && cp <= 0xDFFF)),
        400,
        label + " cần 1–" + max + " ký tự, không chứa ký tự điều khiển.");
    return clean;
  }

  static String id(String value) {
    require(
        value != null
            && value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"),
        400,
        "ID không hợp lệ.");
    return value;
  }

  static String key(String value) {
    require(
        value != null && value.matches("[A-Za-z0-9_-]{8,80}"),
        400,
        "Idempotency-Key cần 8–80 chữ, số, _ hoặc -.");
    return value;
  }

  static long revision(long value) {
    require(value > 0, 400, "Thiếu phiên bản bản ghi.");
    return value;
  }

  static String random(int bytes) {
    byte[] b = new byte[bytes];
    RANDOM.nextBytes(b);
    return HexFormat.of().formatHex(b);
  }

  static String sha(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static boolean equal(String a, String b) {
    return a != null
        && b != null
        && MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  static String instant(long value) {
    return Instant.ofEpochMilli(value).toString();
  }

  static LocalDate today(long now) {
    return Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate();
  }

  static LocalDate day(String value) {
    try {
      require(
          value != null && value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), 400, "Ngày không hợp lệ.");
      return LocalDate.parse(value);
    } catch (Exception e) {
      throw new Problem(400, "Ngày cần dạng YYYY-MM-DD hợp lệ.");
    }
  }

  static void dayRange(LocalDate value, long now) {
    LocalDate today = today(now);
    require(
        !value.isBefore(today) && !value.isAfter(today.plusDays(90)),
        400,
        "Chỉ đặt từ hôm nay đến 90 ngày tiếp theo.");
  }

  static long at(LocalDate date, int hour, int minute) {
    return date.atTime(hour, minute).atZone(ZONE).toInstant().toEpochMilli();
  }

  static Window parseWindow(String startsAt, String endsAt) {
    long start, end;
    try {
      OffsetDateTime a = OffsetDateTime.parse(startsAt), b = OffsetDateTime.parse(endsAt);
      require(
          a.getSecond() == 0 && a.getNano() == 0 && b.getSecond() == 0 && b.getNano() == 0,
          400,
          "Thời gian cần chính xác đến phút.");
      start = a.toInstant().toEpochMilli();
      end = b.toInstant().toEpochMilli();
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(400, "Thời gian cần ISO 8601 kèm múi giờ.");
    }
    LocalDate day = Instant.ofEpochMilli(start).atZone(ZONE).toLocalDate();
    require(start % (15 * MINUTE) == 0 && end % (15 * MINUTE) == 0, 400, "Chọn mốc 15 phút.");
    require(
        end - start >= 15 * MINUTE && end - start <= 240 * MINUTE,
        400,
        "Mỗi lượt đặt từ 15 phút đến bốn giờ.");
    require(
        start >= at(day, 8, 0) && end <= at(day, 20, 0),
        400,
        "Khung đặt là 08:00–20:00 trong cùng ngày, UTC+7.");
    return new Window(start, end, day);
  }

  static void future(Window window, long now) {
    dayRange(window.day, now);
    require(window.start >= now + MINUTE, 400, "Giờ bắt đầu cần cách hiện tại ít nhất một phút.");
  }

  static Window window(String startsAt, String endsAt, long now) {
    Window w = parseWindow(startsAt, endsAt);
    future(w, now);
    return w;
  }

  static int peak(List<Span> spans, long start, long end) {
    TreeMap<Long, Integer> changes = new TreeMap<>();
    for (Span span : spans) {
      long a = Math.max(start, span.start), b = Math.min(end, span.end);
      if (a < b) {
        changes.merge(a, span.units, Integer::sum);
        changes.merge(b, -span.units, Integer::sum);
      }
    }
    int used = 0, peak = 0;
    for (int delta : changes.values()) {
      used += delta;
      peak = Math.max(peak, used);
    }
    return peak;
  }

  private Rules() {}
}
