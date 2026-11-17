package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.Booking;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.model.CourtSession;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.LightController;
import com.example.courtmatepickleballcourtmanager.util.LightScheduler;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class CourtSessionActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private final List<Court> courts = new ArrayList<>();
    private final List<CourtSession> sessions = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private ListView listView;
    private TextView tvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_list);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.management_sessions);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        listView = findViewById(R.id.listItems);
        tvInfo = findViewById(R.id.tvListInfo);
        ExtendedFloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setVisibility(View.GONE);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> openCourt(position));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        courts.clear();
        courts.addAll(db.getAllCourts());
        sessions.clear();
        List<String> rows = new ArrayList<>();
        int playing = 0;
        for (Court court : courts) {
            CourtSession session = db.getActiveSession(court.getId());
            sessions.add(session);
            String title = court.getName();
            String subtitle;
            if (session != null) {
                playing++;
                title += " • Đang chơi";
                String customer = session.customerName == null
                        ? getString(R.string.session_walk_in_customer) : session.customerName;
                subtitle = getString(R.string.session_playing,
                        DateTimeUtils.timePart(session.startTime)) + " • " + customer;
            } else {
                subtitle = getString(R.string.session_empty) + " • "
                        + MoneyUtils.format(court.getPricePerHour()) + "/giờ";
            }
            rows.add(title + "\n" + subtitle);
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(getString(R.string.result_count, courts.size())
                + " • " + playing + " phiên đang chơi");
    }

    private void openCourt(int position) {
        Court court = courts.get(position);
        CourtSession session = sessions.get(position);
        if (session != null) {
            String[] actions = {
                    getString(R.string.view_detail),
                    getString(R.string.finish_session),
                    getString(R.string.cancel)
            };
            new MaterialAlertDialogBuilder(this)
                    .setTitle(court.getName())
                    .setItems(actions, (dialog, which) -> {
                        if (which == 0) {
                            openDetail(session.id);
                        } else if (which == 1) {
                            finishSession(session);
                        }
                    })
                    .show();
            return;
        }
        String[] actions = {
                getString(R.string.start_walk_in),
                getString(R.string.start_from_booking)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.session_choose_action, court.getName()))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        startWalkIn(court);
                    } else {
                        startFromBooking(court);
                    }
                })
                .show();
    }

    private void startWalkIn(Court court) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        layout.setPadding(padding, padding, padding, 0);
        EditText name = input(getString(R.string.customer_name_hint), false);
        EditText phone = input(getString(R.string.phone_hint), true);
        layout.addView(name);
        layout.addView(phone);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.start_walk_in))
                .setView(layout)
                .setPositiveButton(R.string.start, (dialog, which) -> {
                    String customerName = name.getText().toString().trim();
                    String customerPhone = phone.getText().toString().trim();
                    int customerId = 0;
                    if (!customerName.isEmpty() || !customerPhone.isEmpty()) {
                        if (customerName.isEmpty() || customerPhone.isEmpty()) {
                            Toast.makeText(this, R.string.customer_name_required,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        customerId = db.findOrCreateCustomer(customerName, customerPhone);
                        if (customerId < 0) {
                            Toast.makeText(this, R.string.error_customer_phone,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    long id = db.startWalkInSession(court.getId(), customerId,
                            DateTimeUtils.nowDbDateTime());
                    if (id > 0) {
                        LightScheduler.cancel(this, court.getId());
                        LightController.turnOn(this, court.getId(), null);
                        Toast.makeText(this, R.string.session_started, Toast.LENGTH_SHORT).show();
                        openDetail((int) id);
                    } else {
                        Toast.makeText(this, startErrorMessage((int) id),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startFromBooking(Court court) {
        String today = DateTimeUtils.today();
        List<Booking> bookings = db.searchBookings("", today, court.getId(),
                Booking.STATUS_BOOKED);
        if (bookings.isEmpty()) {
            Toast.makeText(this, R.string.session_no_booking, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> labels = new ArrayList<>();
        for (Booking booking : bookings) {
            labels.add(booking.getStartTime() + "–" + booking.getEndTime()
                    + " • " + booking.getCustomerName());
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.session_choose_booking)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    Booking booking = bookings.get(which);
                    long id = db.startBookingSession(booking.getId());
                    if (id > 0) {
                        LightScheduler.scheduleForBooking(this, booking);
                        LightController.turnOn(this, court.getId(), null);
                        Toast.makeText(this, R.string.session_started, Toast.LENGTH_SHORT).show();
                        openDetail((int) id);
                    } else {
                        Toast.makeText(this, startErrorMessage((int) id),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void finishSession(CourtSession session) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.finish_session)
                .setMessage(R.string.finish_and_pay_confirm)
                .setPositiveButton(R.string.payment_continue, (dialog, which) -> {
                    Intent intent = new Intent(this, PaymentActivity.class);
                    intent.putExtra(PaymentActivity.EXTRA_SESSION_ID, session.id);
                    intent.putExtra(PaymentActivity.EXTRA_END_TIME,
                            DateTimeUtils.nowDbDateTime());
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openDetail(int sessionId) {
        Intent intent = new Intent(this, SessionDetailActivity.class);
        intent.putExtra(SessionDetailActivity.EXTRA_SESSION_ID, sessionId);
        startActivity(intent);
    }

    private int startErrorMessage(int code) {
        switch (code) {
            case (int) DatabaseHelper.ERROR_OUTSIDE_WALKIN:
                return R.string.error_outside_walk_in;
            case (int) DatabaseHelper.ERROR_COURT_BUSY:
            case (int) DatabaseHelper.ERROR_SESSION_OVERLAP:
                return R.string.error_court_busy;
            case (int) DatabaseHelper.ERROR_INVALID:
                return R.string.session_none;
            default:
                return R.string.session_invalid;
        }
    }

    private EditText input(String hint, boolean phone) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(phone ? InputType.TYPE_CLASS_PHONE : InputType.TYPE_CLASS_TEXT);
        return editText;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
