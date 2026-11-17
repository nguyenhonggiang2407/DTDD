package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.courtmatepickleballcourtmanager.repository.DashboardRepository;
import com.example.courtmatepickleballcourtmanager.viewmodel.DashboardViewModel;
import com.example.courtmatepickleballcourtmanager.security.AuthSession;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.ActivityLog;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.model.Rental;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.LightScheduler;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DashboardActivity extends ProtectedActivity {

    private DatabaseHelper databaseHelper;
    private DashboardViewModel viewModel;
    private TextView tvDate;
    private TextView tvCourtsActive;
    private TextView tvPlaying;
    private TextView tvReceivable;
    private TextView tvOverdue;
    private TextView tvRevenue;
    private TextView tvBookingsToday;
    private TextView tvDepositsHeld;
    private LinearLayout layoutRecentActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.dashboard_title);

        try {
            databaseHelper = DatabaseHelper.getInstance(this);
        } catch (RuntimeException e) {
            showDatabaseError(e);
            return;
        }

        tvDate = findViewById(R.id.tvDashboardDate);
        tvCourtsActive = findViewById(R.id.tvCourtsActive);
        tvPlaying = findViewById(R.id.tvPlaying);
        tvReceivable = findViewById(R.id.tvReceivable);
        tvOverdue = findViewById(R.id.tvOverdue);
        tvRevenue = findViewById(R.id.tvRevenue);
        tvBookingsToday = findViewById(R.id.tvBookingsToday);
        tvDepositsHeld = findViewById(R.id.tvDepositsHeld);
        layoutRecentActivity = findViewById(R.id.layoutRecentActivity);
        TextView role = findViewById(R.id.tvLoggedInRole);
        role.setText("●  " + AuthSession.username() + "  •  "
                + (AuthSession.isAdmin() ? "CHỦ SÂN" : "NHÂN VIÊN"));
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        viewModel.data().observe(this, this::renderSnapshot);
        viewModel.error().observe(this, message ->
                android.widget.Toast.makeText(this, message,
                        android.widget.Toast.LENGTH_LONG).show());
        findViewById(R.id.btnRecentActivity).setVisibility(AuthSession.isAdmin()
                ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnDashboardStats).setVisibility(AuthSession.isAdmin()
                ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnCloudBookings).setVisibility(AuthSession.isAdmin()
                ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnCloudBookings).setOnClickListener(v ->
                startActivity(new Intent(this, CloudBookingsActivity.class)));

        findViewById(R.id.btnBookings).setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.btnSessions).setOnClickListener(v ->
                startActivity(new Intent(this, ExtendedManagementActivity.class)));
        findViewById(R.id.btnTimeline).setOnClickListener(v ->
                startActivity(new Intent(this, CourtTimelineActivity.class)));
        findViewById(R.id.btnManagement).setOnClickListener(v ->
                startActivity(new Intent(this, ExtendedManagementActivity.class)));
        findViewById(R.id.btnDashboardStats).setOnClickListener(v ->
                startActivity(new Intent(this, StatisticsActivity.class)));
        findViewById(R.id.btnRecentActivity).setOnClickListener(v ->
                startActivity(new Intent(this, AuditLogActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (databaseHelper == null) {
            return;
        }
        if (viewModel != null) viewModel.refresh();
    }

    private void showDatabaseError(RuntimeException error) {
        String detail = error.getMessage() == null ? error.getClass().getSimpleName()
                : error.getMessage();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.database_error_title)
                .setMessage(getString(R.string.database_error_message, detail))
                .setCancelable(false)
                .setPositiveButton(R.string.database_reset, (dialog, which) -> {
                    DatabaseHelper.reset(this);
                    recreate();
                })
                .setNegativeButton(R.string.close, (dialog, which) -> finish())
                .show();
    }

    private void renderSnapshot(DashboardRepository.Snapshot info) {
        tvDate.setText(DateTimeUtils.displayDate(info.date));
        tvCourtsActive.setText(String.valueOf(info.activeCourts));
        tvPlaying.setText(String.valueOf(info.playing));
        tvRevenue.setText(AuthSession.isAdmin() ? MoneyUtils.format(info.revenue) : "—");
        tvReceivable.setText(AuthSession.isAdmin() ? MoneyUtils.format(info.receivable) : "—");
        tvOverdue.setText(String.valueOf(info.overdue));
        tvBookingsToday.setText(getString(R.string.dashboard_bookings_count, info.bookingsToday));
        tvDepositsHeld.setText(AuthSession.isAdmin()
                ? getString(R.string.dashboard_deposits_held,
                        MoneyUtils.format(info.depositsHeld)) : "Tài chính chỉ dành cho chủ sân");
        loadRecentActivity(info.recent);
    }

    private void loadRecentActivity(java.util.List<ActivityLog> logs) {
        layoutRecentActivity.removeAllViews();
        if (logs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.dashboard_no_activity);
            empty.setTextColor(getColor(R.color.chart_text));
            empty.setTextSize(13f);
            layoutRecentActivity.addView(empty);
            return;
        }
        for (ActivityLog log : logs) {
            TextView row = new TextView(this);
            row.setText(DateTimeUtils.displayDateTime(log.time) + " • "
                    + describe(log));
            row.setTextSize(13f);
            row.setPadding(0, 0, 0, dp(10));
            layoutRecentActivity.addView(row);
        }
    }

    private String describe(ActivityLog log) {
        String action = log.action == null ? "" : log.action;
        String target = log.target == null ? "" : log.target;
        if ("THANH_TOAN".equals(action)) {
            return getString(R.string.audit_action_payment);
        }
        if ("HUY_LICH".equals(action)) {
            return getString(R.string.audit_action_cancel_booking);
        }
        if ("XOA_LICH".equals(action)) {
            return getString(R.string.audit_action_delete_booking);
        }
        if ("TRA_THIET_BI".equals(action)) {
            return getString(R.string.audit_action_return_rental);
        }
        if ("NGUNG_KHACH".equals(action)) {
            return getString(R.string.audit_action_deactivate_customer);
        }
        if ("phien_choi".equals(target)) {
            return getString(R.string.target_session) + " #" + log.targetId;
        }
        if ("dat_lich".equals(target)) {
            return getString(R.string.target_booking) + " #" + log.targetId;
        }
        if ("phieu_thue".equals(target)) {
            return getString(R.string.target_rental) + " #" + log.targetId;
        }
        return action;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!AuthSession.isAdmin()) {
            for (int id : new int[]{R.id.action_courts, R.id.action_stats,
                    R.id.action_settings}) {
                MenuItem item = menu.findItem(id);
                if (item != null) item.setVisible(false);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_courts) {
            startActivity(new Intent(this, CourtActivity.class));
            return true;
        }
        if (id == R.id.action_customers) {
            startActivity(new Intent(this, CustomerActivity.class));
            return true;
        }
        if (id == R.id.action_stats) {
            startActivity(new Intent(this, StatisticsActivity.class));
            return true;
        }
        if (id == R.id.action_management) {
            startActivity(new Intent(this, ExtendedManagementActivity.class));
            return true;
        }
        if (id == R.id.action_timeline) {
            startActivity(new Intent(this, CourtTimelineActivity.class));
            return true;
        }
        if (id == R.id.action_calendar) {
            startActivity(new Intent(this, CalendarViewActivity.class));
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.action_change_password) {
            startActivity(new Intent(this, ChangePasswordActivity.class));
            return true;
        }
        if (id == R.id.action_logout) {
            com.example.courtmatepickleballcourtmanager.security.AuthSession.logout();
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            return true;
        }
        if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
