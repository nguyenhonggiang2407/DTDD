package com.example.courtmatepickleballcourtmanager.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.Booking;
import com.example.courtmatepickleballcourtmanager.model.CourtSession;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.model.LightConfig;
import com.example.courtmatepickleballcourtmanager.receiver.LightAutoOffReceiver;

import java.util.List;

public final class LightScheduler {

    public static final String ACTION_AUTO_OFF =
            "com.example.courtmatepickleballcourtmanager.ACTION_LIGHT_AUTO_OFF";
    public static final String EXTRA_COURT_ID = "court_id";
    public static final String EXTRA_BOOKING_ID = "booking_id";
    public static final String EXTRA_END_TIME = "booking_end_time";
    public static final String EXTRA_BOOKING_DATE = "booking_date";
    private static final String PREFS = "light_auto_off";

    private LightScheduler() {
    }

    public static boolean isHandled(Context context, int bookingId,
                                    String bookingDate, String endTime) {
        if (bookingId <= 0 || bookingDate == null || bookingDate.isEmpty()
                || endTime == null || endTime.isEmpty()) {
            return false;
        }
        String key = bookingDate + " " + endTime;
        return key.equals(prefs(context).getString("handled_end_" + bookingId, ""));
    }

    public static void markHandled(Context context, int bookingId,
                                   String bookingDate, String endTime) {
        if (bookingId > 0 && bookingDate != null && !bookingDate.isEmpty()
                && endTime != null && !endTime.isEmpty()) {
            prefs(context).edit().putString("handled_end_" + bookingId,
                    bookingDate + " " + endTime).apply();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** A court uses one PendingIntent; always schedule the earliest eligible booking.
     * Scheduling a single newly saved booking would overwrite an earlier booking's alarm.
     */
    public static void scheduleForBooking(Context context, Booking booking) {
        if (booking != null && booking.getCourtId() > 0) {
            refreshCourt(context, booking.getCourtId());
        }
    }

    public static void scheduleAt(Context context, int courtId, int bookingId,
                                  String bookingDate, String endTime, long triggerAtMillis) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        PendingIntent pending = pendingIntent(context, courtId, bookingId, bookingDate, endTime);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending);
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        triggerAtMillis, pending);
            }
        } catch (SecurityException e) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending);
        }
    }

    public static void cancel(Context context, int courtId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        PendingIntent pending = pendingIntent(context, courtId, 0, "", "");
        manager.cancel(pending);
        pending.cancel();
    }

    /** One active alarm per court, always corresponding to the next upcoming end time. */
    public static void refreshCourt(Context context, int courtId) {
        if (courtId <= 0) {
            return;
        }
        Context app = context.getApplicationContext();
        DatabaseHelper db = DatabaseHelper.getInstance(app);
        cancel(app, courtId);
        LightConfig config = db.getLightConfig(courtId);
        if (config == null || !config.autoOff || !config.isConfigured()) {
            return;
        }
        long now = System.currentTimeMillis();
        // Query all future bookings: the next eligible one can be months away.
        List<Booking> bookings = db.searchBookings("", DateTimeUtils.today(), null,
                courtId, Booking.STATUS_BOOKED);
        Booking earliest = null;
        long nextTrigger = Long.MAX_VALUE;
        for (Booking booking : bookings) {
            long trigger = DateTimeUtils.autoOffTriggerMillis(booking.getDate(),
                    booking.getEndTime(), config.graceMinutes);
            if (trigger <= now || trigger >= nextTrigger
                    || isHandled(app, booking.getId(), booking.getDate(), booking.getEndTime())) {
                continue;
            }
            earliest = booking;
            nextTrigger = trigger;
        }
        if (earliest != null) {
            scheduleAt(app, courtId, earliest.getId(), earliest.getDate(),
                    earliest.getEndTime(), nextTrigger);
        }
    }

    public static void rescheduleAll(Context context) {
        Context app = context.getApplicationContext();
        DatabaseHelper db = DatabaseHelper.getInstance(app);
        for (Court court : db.getAllCourts()) {
            refreshCourt(app, court.getId());
        }
    }

    public static void sweepOverdue(Context context) {
        Context app = context.getApplicationContext();
        DatabaseHelper db = DatabaseHelper.getInstance(app);
        String today = DateTimeUtils.today();
        long now = System.currentTimeMillis();
        List<Booking> bookings = db.searchBookings("", today, today, 0,
                Booking.STATUS_BOOKED);
        for (Booking booking : bookings) {
            LightConfig config = db.getLightConfig(booking.getCourtId());
            if (config == null || !config.autoOff || !config.isConfigured()
                    || isHandled(app, booking.getId(), booking.getDate(), booking.getEndTime())) {
                continue;
            }
            long trigger = DateTimeUtils.autoOffTriggerMillis(booking.getDate(),
                    booking.getEndTime(), config.graceMinutes);
            if (trigger <= 0) {
                continue;
            }
            if (trigger > now) {
                // Do not override the next alarm chosen by refreshCourt().
                continue;
            }
            CourtSession session = db.getActiveSession(booking.getCourtId());
            if (session != null
                    && (!CourtSession.TYPE_BOOKING.equals(session.type)
                    || session.bookingId != booking.getId())) {
                continue;
            }
            markHandled(app, booking.getId(), booking.getDate(), booking.getEndTime());
            LightController.turnOff(app, booking.getCourtId(), null);
        }
    }

    private static PendingIntent pendingIntent(Context context, int courtId, int bookingId,
                                               String bookingDate, String endTime) {
        Intent intent = new Intent(context, LightAutoOffReceiver.class);
        intent.setAction(ACTION_AUTO_OFF);
        intent.putExtra(EXTRA_COURT_ID, courtId);
        intent.putExtra(EXTRA_BOOKING_ID, bookingId);
        intent.putExtra(EXTRA_END_TIME, endTime == null ? "" : endTime);
        intent.putExtra(EXTRA_BOOKING_DATE, bookingDate == null ? "" : bookingDate);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, courtId, intent, flags);
    }
}
