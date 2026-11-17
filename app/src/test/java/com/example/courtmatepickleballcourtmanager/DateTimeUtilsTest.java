package com.example.courtmatepickleballcourtmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;

import org.junit.Test;

public class DateTimeUtilsTest {

    @Test
    public void durationMinutes_returnsDifference() {
        assertEquals(90, DateTimeUtils.durationMinutes("08:00", "09:30"));
        assertEquals(60, DateTimeUtils.durationMinutes("17:00", "18:00"));
    }

    @Test
    public void durationMinutes_invalidRangeReturnsZero() {
        assertEquals(0, DateTimeUtils.durationMinutes("09:00", "08:00"));
        assertEquals(0, DateTimeUtils.durationMinutes("08:00", "08:00"));
    }

    @Test
    public void estimateTotal_fullHour() {
        assertEquals(120000, DateTimeUtils.estimateTotal(120000, "08:00", "09:00"));
    }

    @Test
    public void estimateTotal_ninetyMinutes() {
        assertEquals(180000, DateTimeUtils.estimateTotal(120000, "08:00", "09:30"));
        assertEquals(225000, DateTimeUtils.estimateTotal(150000, "17:00", "18:30"));
    }

    @Test
    public void estimateTotal_isRoundedToThousand() {
        assertEquals(100000, DateTimeUtils.estimateTotal(120000, "08:00", "08:50"));
    }

    @Test
    public void minutes_parsesInvalidValueAsZero() {
        assertEquals(0, DateTimeUtils.minutes("abc"));
        assertEquals(0, DateTimeUtils.minutes(null));
    }

    @Test
    public void utcDateMillis_roundTripsWithoutTimezoneShift() {
        long millis = DateTimeUtils.toUtcDateMillis("2026-10-28");
        assertEquals("2026-10-28", DateTimeUtils.fromUtcDateMillis(millis));
    }

    @Test
    public void utcDateMillis_invalidDateReturnsMinusOne() {
        assertEquals(-1L, DateTimeUtils.toUtcDateMillis(null));
        assertEquals(-1L, DateTimeUtils.toUtcDateMillis("not-a-date"));
    }

    @Test
    public void minutesBetweenDbDateTime_crossesMidnight() {
        assertEquals(90, DateTimeUtils.minutesBetweenDbDateTime(
                "2026-11-03 23:30", "2026-11-04 01:00"));
        assertEquals(30, DateTimeUtils.minutesBetweenDbDateTime(
                "2026-12-31 23:45", "2027-01-01 00:15"));
    }

    @Test
    public void estimateSessionFee_crossesMidnight() {
        assertEquals(180000, DateTimeUtils.estimateSessionFee(
                120000, "2026-11-03 23:00", "2026-11-04 00:30"));
    }

    @Test
    public void addHoursToDbDateTime_crossesMidnight() {
        assertEquals("2026-11-04 01:30", DateTimeUtils.addHoursToDbDateTime(
                "2026-11-03 23:30", 2));
    }

    @Test
    public void addHoursToDbDateTime_invalidInputReturnsInput() {
        assertEquals("not-a-date", DateTimeUtils.addHoursToDbDateTime("not-a-date", 2));
    }

    @Test
    public void toEpochMillis_parsesDateAndTime() {
        long millis = DateTimeUtils.toEpochMillis("2026-11-03", "18:30");
        assertTrue(millis > 0);
        assertEquals(-1L, DateTimeUtils.toEpochMillis(null, "18:30"));
        assertEquals(-1L, DateTimeUtils.toEpochMillis("not-a-date", "18:30"));
        assertEquals(-1L, DateTimeUtils.toEpochMillis("2026-11-03", null));
    }

    @Test
    public void autoOffTriggerMillis_addsGraceMinutes() {
        long end = DateTimeUtils.toEpochMillis("2026-11-03", "18:30");
        assertEquals(end, DateTimeUtils.autoOffTriggerMillis("2026-11-03", "18:30", 0));
        assertEquals(end + 10 * 60_000L,
                DateTimeUtils.autoOffTriggerMillis("2026-11-03", "18:30", 10));
        assertEquals(end, DateTimeUtils.autoOffTriggerMillis("2026-11-03", "18:30", -5));
        assertEquals(-1L, DateTimeUtils.autoOffTriggerMillis("bad", "18:30", 5));
    }

    @Test
    public void roundToThousand_roundsNearestThousand() {
        assertEquals(100000, DateTimeUtils.roundToThousand(99999));
        assertEquals(100000, DateTimeUtils.roundToThousand(100499));
        assertEquals(101000, DateTimeUtils.roundToThousand(100500));
    }
}
