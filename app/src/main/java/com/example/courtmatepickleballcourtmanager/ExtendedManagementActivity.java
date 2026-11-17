package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.Booking;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.model.Rental;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.google.android.material.appbar.MaterialToolbar;

public class ExtendedManagementActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private TextView tvOverviewSessions;
    private TextView tvOverviewBookings;
    private TextView tvOverviewOverdue;
    private TextView tvOverviewRevenue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_management);

        tvOverviewSessions = findViewById(R.id.tvOverviewSessions);
        tvOverviewBookings = findViewById(R.id.tvOverviewBookings);
        tvOverviewOverdue = findViewById(R.id.tvOverviewOverdue);
        tvOverviewRevenue = findViewById(R.id.tvOverviewRevenue);
        findViewById(R.id.cardOverview).setOnClickListener(v ->
                startActivity(new Intent(this, CourtSessionActivity.class)));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.management_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btnSessions).setOnClickListener(v ->
                startActivity(new Intent(this, CourtSessionActivity.class)));
        findViewById(R.id.btnInvoices).setOnClickListener(v ->
                startActivity(new Intent(this, InvoiceActivity.class)));
        findViewById(R.id.btnServices).setOnClickListener(v ->
                startActivity(new Intent(this, ServiceManagementActivity.class)));
        findViewById(R.id.btnWalkIn).setOnClickListener(v ->
                startActivity(new Intent(this, WalkInSlotActivity.class)));
        findViewById(R.id.btnRentals).setOnClickListener(v ->
                startActivity(new Intent(this, RentalActivity.class)));
        findViewById(R.id.btnCoaches).setOnClickListener(v ->
                startActivity(new Intent(this, CoachManagementActivity.class)));
        findViewById(R.id.btnClasses).setOnClickListener(v ->
                startActivity(new Intent(this, ClassManagementActivity.class)));
        findViewById(R.id.btnEvents).setOnClickListener(v ->
                startActivity(new Intent(this, EventManagementActivity.class)));
        findViewById(R.id.btnPromotions).setOnClickListener(v ->
                startActivity(new Intent(this, PromotionManagementActivity.class)));
        findViewById(R.id.btnSpecialDates).setOnClickListener(v ->
                startActivity(new Intent(this, SpecialDateActivity.class)));
        findViewById(R.id.btnLights).setOnClickListener(v ->
                startActivity(new Intent(this, LightConfigActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOverview();
    }

    private void loadOverview() {
        int activeSessions = 0;
        for (Court court : db.getAllCourts()) {
            if (db.getActiveSession(court.getId()) != null) {
                activeSessions++;
            }
        }
        String today = DateTimeUtils.today();
        int bookings = db.countByStatus(today, today, Booking.STATUS_BOOKED);
        db.refreshOverdueRentals(DateTimeUtils.nowDbDateTime());
        int overdue = db.getRentals(Rental.STATUS_OVERDUE).size();
        int revenue = db.getRevenueSummary(today, today).total;

        tvOverviewSessions.setText(String.valueOf(activeSessions));
        tvOverviewBookings.setText(String.valueOf(bookings));
        tvOverviewOverdue.setText(String.valueOf(overdue));
        tvOverviewRevenue.setText(MoneyUtils.format(revenue));
    }
}
