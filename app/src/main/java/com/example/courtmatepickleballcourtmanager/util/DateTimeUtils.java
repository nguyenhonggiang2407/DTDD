package com.example.courtmatepickleballcourtmanager.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class DateTimeUtils {

    private static final String DB_DATE = "yyyy-MM-dd";
    private static final String DB_DATE_TIME = "yyyy-MM-dd HH:mm";
    private static final String DB_TIME = "HH:mm";
    private static final String DISPLAY_DATE = "dd/MM/yyyy";
    private static final String DISPLAY_DATE_TIME = "dd/MM/yyyy HH:mm";
    private static final Locale LOCALE = Locale.US;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private DateTimeUtils() {
    }

    public static String today() {
        return formatDbDate(new Date());
    }

    public static String formatDbDate(Date date) {
        return new SimpleDateFormat(DB_DATE, LOCALE).format(date);
    }

    public static String displayDate(String dbDate) {
        Date date = parseDbDate(dbDate);
        if (date == null) {
            return dbDate;
        }
        return new SimpleDateFormat(DISPLAY_DATE, LOCALE).format(date);
    }

    public static Date parseDbDate(String dbDate) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(DB_DATE, LOCALE);
            format.setLenient(false);
            return format.parse(dbDate);
        } catch (ParseException | NullPointerException e) {
            return null;
        }
    }

    public static boolean isBeforeToday(String dbDate) {
        return dbDate != null && dbDate.compareTo(today()) < 0;
    }

    public static long toUtcDateMillis(String dbDate) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(DB_DATE, LOCALE);
            format.setLenient(false);
            format.setTimeZone(UTC);
            Date date = format.parse(dbDate);
            return date == null ? -1L : date.getTime();
        } catch (ParseException | NullPointerException e) {
            return -1L;
        }
    }

    public static String fromUtcDateMillis(long millis) {
        SimpleDateFormat format = new SimpleDateFormat(DB_DATE, LOCALE);
        format.setTimeZone(UTC);
        return format.format(new Date(millis));
    }

    public static int minutes(String time) {
        if (time == null) {
            return 0;
        }
        String[] parts = time.split(":");
        if (parts.length != 2) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int durationMinutes(String startTime, String endTime) {
        int start = minutes(startTime);
        int end = minutes(endTime);
        return Math.max(0, end - start);
    }

    public static int estimateTotal(int pricePerHour, String startTime, String endTime) {
        double hours = durationMinutes(startTime, endTime) / 60.0;
        return (int) Math.round(hours * pricePerHour / 1000.0) * 1000;
    }

    public static String monthKey(int year, int month) {
        return String.format(LOCALE, "%04d-%02d", year, month);
    }

    public static String nowDbDateTime() {
        return new SimpleDateFormat(DB_DATE_TIME, LOCALE).format(new Date());
    }

    public static String nowDbTime() {
        return new SimpleDateFormat(DB_TIME, LOCALE).format(new Date());
    }

    public static String formatDbDateTime(Date date) {
        return new SimpleDateFormat(DB_DATE_TIME, LOCALE).format(date);
    }

    public static Date parseDbDateTime(String value) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(DB_DATE_TIME, LOCALE);
            format.setLenient(false);
            return format.parse(value);
        } catch (ParseException | NullPointerException e) {
            return null;
        }
    }

    public static long toEpochMillis(String dbDate, String time) {
        if (dbDate == null || time == null) {
            return -1L;
        }
        Date date = parseDbDateTime(dbDate.trim() + " " + time.trim());
        return date == null ? -1L : date.getTime();
    }

    public static long autoOffTriggerMillis(String dbDate, String endTime, int graceMinutes) {
        long end = toEpochMillis(dbDate, endTime);
        if (end <= 0) {
            return -1L;
        }
        return end + Math.max(0, graceMinutes) * 60_000L;
    }

    public static String displayDateTime(String value) {
        Date date = parseDbDateTime(value);
        if (date == null) {
            return value;
        }
        return new SimpleDateFormat(DISPLAY_DATE_TIME, LOCALE).format(date);
    }

    public static String datePart(String dateTime) {
        return dateTime == null || dateTime.length() < 10 ? "" : dateTime.substring(0, 10);
    }

    public static String timePart(String dateTime) {
        return dateTime == null || dateTime.length() < 16 ? "" : dateTime.substring(11, 16);
    }

    public static int weekday(String dbDate) {
        Date date = parseDbDate(dbDate);
        if (date == null) {
            return 1;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SUNDAY ? 7 : day - 1;
    }

    public static int minutesBetweenDbDateTime(String start, String end) {
        Date startDate = parseDbDateTime(start);
        Date endDate = parseDbDateTime(end);
        if (startDate == null || endDate == null) {
            return 0;
        }
        return Math.max(0, (int) ((endDate.getTime() - startDate.getTime()) / 60000L));
    }

    public static int estimateSessionFee(int pricePerHour, String startDateTime,
                                         String endDateTime) {
        int duration = minutesBetweenDbDateTime(startDateTime, endDateTime);
        double hours = duration / 60.0;
        return roundToThousand((int) Math.round(hours * pricePerHour));
    }

    public static int roundToThousand(int value) {
        return (int) (Math.round(value / 1000.0) * 1000L);
    }

    public static String addHoursToDbDateTime(String dateTime, int hours) {
        Date date = parseDbDateTime(dateTime);
        if (date == null) {
            return dateTime;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.HOUR_OF_DAY, hours);
        return formatDbDateTime(calendar.getTime());
    }

    public static String todayDateTimeAt(String time) {
        return today() + " " + time;
    }

    public static boolean isTimeInRange(String time, String start, String end) {
        int value = minutes(time);
        return value >= minutes(start) && value < minutes(end);
    }

    public static boolean isDateTimeInRange(String dateTime, String start, String end) {
        return dateTime != null && start != null && end != null
                && dateTime.compareTo(start) >= 0 && dateTime.compareTo(end) < 0;
    }

    public static String shiftYears(String dbDate, int years) {
        Date date = parseDbDate(dbDate);
        if (date == null) {
            return dbDate;
        }
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.setTime(date);
        calendar.add(Calendar.YEAR, years);
        return formatDbDate(calendar.getTime());
    }
}
