package com.example.courtmatepickleballcourtmanager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.Booking;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.example.courtmatepickleballcourtmanager.view.BarChartView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatisticsActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private final Calendar month = Calendar.getInstance();

    private TextView tvMonth;
    private TextView tvRevenue;
    private TextView tvRevenueBreakdown;
    private TextView tvRevenuePosition;
    private TextView tvBookingCount;
    private TextView tvCancelledCount;
    private TextView tvOccupancy;
    private LinearProgressIndicator progressOccupancy;
    private BarChartView chartRevenue;
    private LinearLayout layoutTopCourts;
    private MaterialButton btnCurrentMonth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_stats);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.title_stats);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvMonth = findViewById(R.id.tvMonth);
        tvRevenue = findViewById(R.id.tvRevenue);
        tvRevenueBreakdown = findViewById(R.id.tvRevenueBreakdown);
        tvRevenuePosition = findViewById(R.id.tvRevenuePosition);
        tvBookingCount = findViewById(R.id.tvBookingCount);
        tvCancelledCount = findViewById(R.id.tvCancelledCount);
        tvOccupancy = findViewById(R.id.tvOccupancy);
        progressOccupancy = findViewById(R.id.progressOccupancy);
        chartRevenue = findViewById(R.id.chartRevenue);
        layoutTopCourts = findViewById(R.id.layoutTopCourts);
        btnCurrentMonth = findViewById(R.id.btnCurrentMonth);

        month.set(Calendar.DAY_OF_MONTH, 1);

        findViewById(R.id.btnPrevMonth).setOnClickListener(v -> {
            month.add(Calendar.MONTH, -1);
            month.set(Calendar.DAY_OF_MONTH, 1);
            load();
        });
        findViewById(R.id.btnNextMonth).setOnClickListener(v -> {
            month.add(Calendar.MONTH, 1);
            month.set(Calendar.DAY_OF_MONTH, 1);
            load();
        });
        btnCurrentMonth.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            month.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), 1);
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

    private void load() {
        int year = month.get(Calendar.YEAR);
        int monthIndex = month.get(Calendar.MONTH);
        int lastDay = month.getActualMaximum(Calendar.DAY_OF_MONTH);

        String prefix = DateTimeUtils.monthKey(year, monthIndex + 1);
        String fromDate = prefix + "-01";
        String toDate = String.format(Locale.US, "%s-%02d", prefix, lastDay);

        tvMonth.setText(getString(R.string.stats_month, monthIndex + 1, year));
        Calendar now = Calendar.getInstance();
        boolean viewingCurrentMonth = year == now.get(Calendar.YEAR)
                && monthIndex == now.get(Calendar.MONTH);
        btnCurrentMonth.setVisibility(viewingCurrentMonth ? View.GONE : View.VISIBLE);

        DatabaseHelper.RevenueSummary summary = db.getRevenueSummary(fromDate, toDate);
        int booked = db.countByStatus(fromDate, toDate, Booking.STATUS_BOOKED);
        int cancelled = db.countByStatus(fromDate, toDate, Booking.STATUS_CANCELLED);

        tvRevenue.setText(MoneyUtils.format(summary.total));
        tvRevenueBreakdown.setText(getString(R.string.stats_revenue_breakdown,
                MoneyUtils.format(summary.court), MoneyUtils.format(summary.service),
                MoneyUtils.format(summary.rental), MoneyUtils.format(summary.classFee),
                MoneyUtils.format(summary.eventFee)));
        tvRevenuePosition.setText(getString(R.string.stats_revenue_position,
                MoneyUtils.format(summary.total), MoneyUtils.format(summary.receivable),
                MoneyUtils.format(db.getDepositsHeld())));
        tvBookingCount.setText(String.valueOf(booked));
        tvCancelledCount.setText(String.valueOf(cancelled));

        int activeCourts = 0;
        for (Court court : db.getAllCourts()) {
            if (court.isActive()) {
                activeCourts++;
            }
        }
        int capacityMinutes = activeCourts * 16 * 60 * lastDay;
        int bookedMinutes = db.sumSessionMinutes(fromDate, toDate);
        int occupancy = capacityMinutes == 0 ? 0
                : Math.min(100, Math.round(bookedMinutes * 100f / capacityMinutes));
        tvOccupancy.setText(getString(R.string.stats_occupancy, occupancy));
        progressOccupancy.setProgressCompat(occupancy, true);

        Map<Integer, Integer> revenueByDay = new HashMap<>();
        for (DatabaseHelper.DayRevenue day : db.revenueByDay(fromDate, toDate)) {
            try {
                int dayOfMonth = Integer.parseInt(day.getDate().substring(8, 10));
                revenueByDay.put(dayOfMonth, day.getRevenue());
            } catch (NumberFormatException ignored) {
            }
        }

        List<BarChartView.Entry> entries = new ArrayList<>();
        for (int day = 1; day <= lastDay; day++) {
            Integer value = revenueByDay.get(day);
            entries.add(new BarChartView.Entry(day, value == null ? 0 : value));
        }
        chartRevenue.setEntries(entries);

        layoutTopCourts.removeAllViews();
        List<DatabaseHelper.CourtStat> topCourts = db.topCourts(fromDate, toDate, 5);
        if (topCourts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.stats_no_data);
            empty.setTextColor(ContextCompat.getColor(this, R.color.chart_text));
            int padding = dp(8);
            empty.setPadding(0, padding, 0, padding);
            layoutTopCourts.addView(empty);
        } else {
            LayoutInflater inflater = LayoutInflater.from(this);
            for (int i = 0; i < topCourts.size(); i++) {
                DatabaseHelper.CourtStat stat = topCourts.get(i);
                View row = inflater.inflate(R.layout.item_stat_court, layoutTopCourts, false);
                TextView tvRank = row.findViewById(R.id.tvRank);
                TextView tvName = row.findViewById(R.id.tvStatCourtName);
                TextView tvBookings = row.findViewById(R.id.tvStatBookings);
                TextView tvRevenue = row.findViewById(R.id.tvStatRevenue);
                tvRank.setText(String.valueOf(i + 1));
                tvName.setText(stat.getName());
                tvBookings.setText(getString(R.string.stats_top_bookings, stat.getBookings()));
                tvRevenue.setText(MoneyUtils.format(stat.getRevenue()));
                layoutTopCourts.addView(row);
            }
        }

        LinearLayout layoutTopCustomers = findViewById(R.id.layoutTopCustomers);
        if (layoutTopCustomers != null) {
            layoutTopCustomers.removeAllViews();
            List<DatabaseHelper.TopCustomer> topCustomers = db.getTopCustomers(fromDate, toDate, 5);
            if (topCustomers.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText(R.string.stats_no_data);
                empty.setTextColor(ContextCompat.getColor(this, R.color.chart_text));
                int padding = dp(8);
                empty.setPadding(0, padding, 0, padding);
                layoutTopCustomers.addView(empty);
            } else {
                LayoutInflater inflater = LayoutInflater.from(this);
                for (int i = 0; i < topCustomers.size(); i++) {
                    DatabaseHelper.TopCustomer tc = topCustomers.get(i);
                    View row = inflater.inflate(R.layout.item_stat_court, layoutTopCustomers, false);
                    TextView tvRank = row.findViewById(R.id.tvRank);
                    TextView tvName = row.findViewById(R.id.tvStatCourtName);
                    TextView tvBookings = row.findViewById(R.id.tvStatBookings);
                    TextView tvRevenue = row.findViewById(R.id.tvStatRevenue);
                    tvRank.setText(String.valueOf(i + 1));
                    tvName.setText(tc.getName());
                    tvBookings.setText(getString(R.string.stats_bookings_count, tc.getBookings()));
                    tvRevenue.setText(MoneyUtils.format(tc.getRevenue()));
                    layoutTopCustomers.addView(row);
                }
            }
        }

        LinearLayout layoutPeakHours = findViewById(R.id.layoutPeakHours);
        if (layoutPeakHours != null) {
            layoutPeakHours.removeAllViews();
            List<DatabaseHelper.PeakHour> peakHours = db.getPeakHours(fromDate, toDate);
            if (peakHours.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText(R.string.stats_no_data);
                empty.setTextColor(ContextCompat.getColor(this, R.color.chart_text));
                int padding = dp(8);
                empty.setPadding(0, padding, 0, padding);
                layoutPeakHours.addView(empty);
            } else {
                LayoutInflater inflater = LayoutInflater.from(this);
                for (int i = 0; i < Math.min(5, peakHours.size()); i++) {
                    DatabaseHelper.PeakHour ph = peakHours.get(i);
                    View row = inflater.inflate(R.layout.item_stat_court, layoutPeakHours, false);
                    TextView tvRank = row.findViewById(R.id.tvRank);
                    TextView tvName = row.findViewById(R.id.tvStatCourtName);
                    TextView tvBookings = row.findViewById(R.id.tvStatBookings);
                    TextView tvRevenue = row.findViewById(R.id.tvStatRevenue);
                    tvRank.setText(String.valueOf(i + 1));
                    tvName.setText(getString(R.string.stats_hour_format, ph.getHour()));
                    tvBookings.setText(getString(R.string.stats_bookings_count, ph.getBookings()));
                    tvRevenue.setVisibility(View.GONE);
                    layoutPeakHours.addView(row);
                }
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
