package com.example.courtmatepickleballcourtmanager.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.CourtSession;
import com.example.courtmatepickleballcourtmanager.model.Booking;
import com.example.courtmatepickleballcourtmanager.model.LightConfig;
import com.example.courtmatepickleballcourtmanager.util.LightController;
import com.example.courtmatepickleballcourtmanager.util.LightScheduler;

public class LightAutoOffReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int courtId = intent.getIntExtra(LightScheduler.EXTRA_COURT_ID, 0);
        int bookingId = intent.getIntExtra(LightScheduler.EXTRA_BOOKING_ID, 0);
        String endTime = intent.getStringExtra(LightScheduler.EXTRA_END_TIME);
        String bookingDate = intent.getStringExtra(LightScheduler.EXTRA_BOOKING_DATE);
        if (courtId <= 0) {
            return;
        }
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            LightConfig config = db.getLightConfig(courtId);
            Booking booking = db.getBooking(bookingId);
            // An alarm may become stale after a booking is moved, cancelled or deleted.
            if (config == null || !config.autoOff || !config.isConfigured()
                    || booking == null
                    || !Booking.STATUS_BOOKED.equals(booking.getStatus())
                    || booking.getCourtId() != courtId
                    || !booking.getEndTime().equals(endTime)
                    || !booking.getDate().equals(bookingDate)
                    || LightScheduler.isHandled(context, bookingId, bookingDate, endTime)) {
                return;
            }
            CourtSession session = db.getActiveSession(courtId);
            if (session != null
                    && (!CourtSession.TYPE_BOOKING.equals(session.type)
                    || session.bookingId != bookingId)) {
                return;
            }
            LightScheduler.markHandled(context, bookingId, bookingDate, endTime);
            LightController.turnOff(context, courtId, null);
        } finally {
            // Once the current alarm is consumed, schedule the next booking for this court.
            LightScheduler.refreshCourt(context, courtId);
        }
    }
}
