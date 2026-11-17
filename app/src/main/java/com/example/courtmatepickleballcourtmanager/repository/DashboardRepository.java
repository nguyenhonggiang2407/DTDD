package com.example.courtmatepickleballcourtmanager.repository;

import android.content.Context;
import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.ActivityLog;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.model.Rental;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.LightScheduler;
import com.example.courtmatepickleballcourtmanager.security.AuthSession;
import java.util.Collections;
import java.util.List;

/** Read-only orchestration. Activity no longer performs multiple DB queries on UI thread. */
public final class DashboardRepository {
    private final DatabaseHelper db;
    private final Context context;
    public DashboardRepository(Context context) {
        this.context = context.getApplicationContext();
        db = DatabaseHelper.getInstance(this.context);
    }
    public static final class Snapshot {
        public String date;
        public int activeCourts, playing, overdue, bookingsToday, revenue, receivable, depositsHeld;
        public List<ActivityLog> recent;
    }
    public Snapshot load() {
        LightScheduler.rescheduleAll(context);
        LightScheduler.sweepOverdue(context);
        Snapshot s = new Snapshot();
        s.date = DateTimeUtils.today();
        for (Court court : db.getActiveCourts()) {
            s.activeCourts++;
            if (db.getActiveSession(court.getId()) != null) s.playing++;
        }
        if (AuthSession.isAdmin()) {
            DatabaseHelper.RevenueSummary rev = db.getRevenueSummary(s.date, s.date);
            s.revenue = rev == null ? 0 : rev.total;
            s.receivable = db.getReceivable();
        }
        db.refreshOverdueRentals(DateTimeUtils.nowDbDateTime());
        s.overdue = db.getRentals(Rental.STATUS_OVERDUE).size();
        s.bookingsToday = db.searchBookings("", s.date, s.date, 0, null).size();
        s.depositsHeld = AuthSession.isAdmin() ? db.getDepositsHeld() : 0;
        s.recent = AuthSession.isAdmin()
                ? db.getRecentActivities(4) : Collections.emptyList();
        return s;
    }
}
