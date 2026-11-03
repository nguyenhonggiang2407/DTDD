package vn.yang.slots;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import vn.yang.slots.Contracts.ReservationView;

final class Exports {
  static String csv(List<ReservationView> rows) {
    StringBuilder out = new StringBuilder("\ufeff");
    out.append(
        line(
            List.of(
                "ID",
                "Tài nguyên",
                "Tên lượt đặt",
                "Số đơn vị",
                "Bắt đầu UTC",
                "Kết thúc UTC",
                "Trạng thái",
                "Phiên bản")));
    for (ReservationView row : rows)
      out.append(
          line(
              List.of(
                  row.id(),
                  row.resourceName(),
                  row.title(),
                  row.units(),
                  row.startsAt(),
                  row.endsAt(),
                  row.status(),
                  row.version())));
    return out.toString();
  }

  private static String line(List<?> values) {
    List<String> cells = new ArrayList<>();
    for (Object value : values) {
      String text = value.toString();
      if (value instanceof String && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0)
        text = "'" + text;
      cells.add("\"" + text.replace("\"", "\"\"") + "\"");
    }
    return String.join(",", cells) + "\r\n";
  }

  private static String icsText(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;");
  }

  static String fold(String line) {
    StringBuilder out = new StringBuilder();
    int bytes = 0;
    for (int offset = 0; offset < line.length(); ) {
      int cp = line.codePointAt(offset);
      String token = new String(Character.toChars(cp));
      int length = token.getBytes(StandardCharsets.UTF_8).length;
      if (bytes + length > 75) {
        out.append("\r\n ");
        bytes = 1;
      }
      out.append(token);
      bytes += length;
      offset += Character.charCount(cp);
    }
    return out + "\r\n";
  }

  private static String stamp(String value) {
    return DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)
        .format(Instant.parse(value));
  }

  static String calendar(List<ReservationView> rows, long now) {
    StringBuilder out =
        new StringBuilder(
            "BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:-//Nguyen Hong Giang//YangSlots//VI\r\n"
                + "CALSCALE:GREGORIAN\r\n");
    for (ReservationView row : rows) {
      if (!row.status().equals("CONFIRMED")) continue;
      for (String line :
          List.of(
              "BEGIN:VEVENT",
              "UID:" + row.id() + "@yangslots",
              "DTSTAMP:" + stamp(Instant.ofEpochMilli(now).toString()),
              "DTSTART:" + stamp(row.startsAt()),
              "DTEND:" + stamp(row.endsAt()),
              "SUMMARY:" + icsText(row.title()),
              "LOCATION:" + icsText(row.resourceName()),
              "DESCRIPTION:" + icsText(row.units() + " đơn vị · " + row.resourceName()),
              "STATUS:CONFIRMED",
              "SEQUENCE:" + row.version(),
              "END:VEVENT")) out.append(fold(line));
    }
    return out + "END:VCALENDAR\r\n";
  }

  private Exports() {}
}
