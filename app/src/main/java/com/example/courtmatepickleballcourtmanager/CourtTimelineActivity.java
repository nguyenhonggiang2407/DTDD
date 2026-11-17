package com.example.courtmatepickleballcourtmanager;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.Booking;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.model.CourtSession;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CourtTimelineActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private String selectedDate;
    private TextView tvDate;
    private TextView tvInfo;
    private TimelineAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_timeline);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.timeline_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        selectedDate = DateTimeUtils.today();
        tvDate = findViewById(R.id.tvTimelineDate);
        tvInfo = findViewById(R.id.tvTimelineInfo);
        ListView listView = findViewById(R.id.listTimeline);
        adapter = new TimelineAdapter(this);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                showDetail(adapter.getItem(position)));

        findViewById(R.id.btnPrevDay).setOnClickListener(v ->
                shiftDate(-1));
        findViewById(R.id.btnNextDay).setOnClickListener(v ->
                shiftDate(1));
        findViewById(R.id.btnToday).setOnClickListener(v -> {
            selectedDate = DateTimeUtils.today();
            load();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shiftDate(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(DateTimeUtils.parseDbDate(selectedDate));
        calendar.add(Calendar.DAY_OF_MONTH, days);
        selectedDate = DateTimeUtils.formatDbDate(calendar.getTime());
        load();
    }

    private void load() {
        tvDate.setText(DateTimeUtils.displayDate(selectedDate));
        List<Court> courts = db.getAllCourts();
        List<Booking> bookings = db.searchBookings("", selectedDate, selectedDate,
                0, Booking.STATUS_BOOKED);
        Map<Integer, List<Booking>> bookingsByCourt = new HashMap<>();
        for (Booking booking : bookings) {
            List<Booking> list = bookingsByCourt.get(booking.getCourtId());
            if (list == null) {
                list = new ArrayList<>();
                bookingsByCourt.put(booking.getCourtId(), list);
            }
            list.add(booking);
        }

        List<TimelineRow> rows = new ArrayList<>();
        int playing = 0;
        String today = DateTimeUtils.today();
        for (Court court : courts) {
            TimelineRow row = new TimelineRow();
            row.court = court;
            CourtSession active = db.getActiveSession(court.getId());
            boolean showActive = active != null
                    && (selectedDate.equals(DateTimeUtils.datePart(active.startTime))
                    || (selectedDate.equals(today)
                    && DateTimeUtils.datePart(active.startTime).compareTo(selectedDate) < 0));
            List<Booking> courtBookings = bookingsByCourt.get(court.getId());
            if (!court.isActive()) {
                row.status = getString(R.string.timeline_court_maintenance);
                row.detail = getString(R.string.timeline_no_slot);
                row.bg = R.drawable.bg_status_maintenance;
                row.textColor = R.color.status_maintenance_text;
            } else if (showActive) {
                playing++;
                row.status = getString(R.string.timeline_court_playing);
                StringBuilder detail = new StringBuilder(getString(
                        R.string.timeline_playing_since,
                        DateTimeUtils.timePart(active.startTime)));
                if (!selectedDate.equals(DateTimeUtils.datePart(active.startTime))) {
                    detail.append(getString(R.string.timeline_cross_midnight));
                }
                if (active.customerName != null && !active.customerName.isEmpty()) {
                    detail.append(" • ").append(active.customerName);
                }
                row.detail = detail.toString();
                row.bg = R.drawable.bg_status_cancelled;
                row.textColor = R.color.status_cancelled_text;
            } else if (courtBookings != null && !courtBookings.isEmpty()) {
                row.status = getString(R.string.timeline_court_upcoming);
                row.detail = bookingSummary(courtBookings);
                row.bg = R.drawable.bg_status_maintenance;
                row.textColor = R.color.status_maintenance_text;
            } else {
                row.status = getString(R.string.timeline_court_free);
                row.detail = getString(R.string.timeline_no_slot);
                row.bg = R.drawable.bg_status_active;
                row.textColor = R.color.status_active_text;
            }
            rows.add(row);
        }
        adapter.setRows(rows);
        tvInfo.setText(getString(R.string.timeline_summary, courts.size(), playing,
                bookings.size()));
    }

    private String bookingSummary(List<Booking> bookings) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < bookings.size(); i++) {
            Booking booking = bookings.get(i);
            if (i > 0) {
                builder.append("\n");
            }
            builder.append(booking.getStartTime()).append("–")
                    .append(booking.getEndTime());
            if (booking.getCustomerName() != null && !booking.getCustomerName().isEmpty()) {
                builder.append(" • ").append(booking.getCustomerName());
            }
        }
        return builder.toString();
    }

    private void showDetail(TimelineRow row) {
        String message = row.detail == null ? "" : row.detail;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(row.court.getName())
                .setMessage(message)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private static class TimelineRow {
        Court court;
        String status;
        String detail;
        int bg;
        int textColor;
    }

    private static class TimelineAdapter extends BaseAdapter {

        private final LayoutInflater inflater;
        private final Context context;
        private final List<TimelineRow> rows = new ArrayList<>();

        TimelineAdapter(Context context) {
            this.context = context;
            this.inflater = LayoutInflater.from(context);
        }

        void setRows(List<TimelineRow> newRows) {
            rows.clear();
            rows.addAll(newRows);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public TimelineRow getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return rows.get(position).court.getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = inflater.inflate(R.layout.item_court, parent, false);
            }
            TimelineRow row = rows.get(position);
            TextView tvAvatar = view.findViewById(R.id.tvCourtAvatar);
            TextView tvName = view.findViewById(R.id.tvCourtName);
            TextView tvDetail = view.findViewById(R.id.tvCourtPrice);
            TextView tvStatus = view.findViewById(R.id.tvCourtStatus);

            String name = row.court.getName() == null ? "" : row.court.getName().trim();
            tvAvatar.setText(name.isEmpty() ? ""
                    : name.substring(0, 1).toUpperCase(Locale.getDefault()));
            tvName.setText(row.court.getName());
            tvDetail.setText(row.detail);
            tvStatus.setText(row.status);
            tvStatus.setBackgroundResource(row.bg);
            tvStatus.setTextColor(ContextCompat.getColor(context, row.textColor));
            return view;
        }
    }
}
