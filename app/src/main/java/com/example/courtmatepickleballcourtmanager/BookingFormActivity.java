package com.example.courtmatepickleballcourtmanager;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.Booking;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.model.Customer;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.LightScheduler;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BookingFormActivity extends ProtectedActivity {

    public static final String EXTRA_BOOKING_ID = "extra_booking_id";
    public static final String EXTRA_DUPLICATE_FROM = "extra_duplicate_from";

    private DatabaseHelper db;

    private MaterialAutoCompleteTextView actCourt;
    private TextInputEditText etCustomerName;
    private TextInputEditText etCustomerPhone;
    private TextInputEditText etNote;
    private MaterialButton btnDate;
    private MaterialButton btnStartTime;
    private MaterialButton btnEndTime;
    private TextView tvEstimate;

    private final List<Court> courts = new ArrayList<>();
    private Court selectedCourt;
    private String selectedDate;
    private String startTime = "08:00";
    private String endTime = "09:00";

    private int bookingId = -1;
    private Booking editingBooking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_booking_form);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.title_booking_new);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        actCourt = findViewById(R.id.actCourt);
        etCustomerName = findViewById(R.id.etCustomerName);
        etCustomerPhone = findViewById(R.id.etCustomerPhone);
        etNote = findViewById(R.id.etNote);
        btnDate = findViewById(R.id.btnDate);
        btnStartTime = findViewById(R.id.btnStartTime);
        btnEndTime = findViewById(R.id.btnEndTime);
        tvEstimate = findViewById(R.id.tvEstimate);

        selectedDate = DateTimeUtils.today();
        bookingId = getIntent().getIntExtra(EXTRA_BOOKING_ID, -1);
        int duplicateFromId = getIntent().getIntExtra(EXTRA_DUPLICATE_FROM, -1);

        if (bookingId != -1) {
            editingBooking = db.getBooking(bookingId);
            if (editingBooking == null) {
                finish();
                return;
            }
            selectedDate = editingBooking.getDate();
            startTime = editingBooking.getStartTime();
            endTime = editingBooking.getEndTime();
            setTitle(R.string.title_booking_edit);
        } else if (duplicateFromId != -1) {
            editingBooking = db.getBooking(duplicateFromId);
            if (editingBooking != null) {
                selectedDate = DateTimeUtils.today();
                startTime = editingBooking.getStartTime();
                endTime = editingBooking.getEndTime();
                bookingId = -1;
                setTitle(R.string.add_booking);
            }
        }

        loadCourts();

        if (editingBooking != null) {
            etCustomerName.setText(editingBooking.getCustomerName());
            etCustomerPhone.setText(editingBooking.getCustomerPhone());
            etNote.setText(editingBooking.getNote());
        }

        actCourt.setThreshold(0);
        actCourt.setOnItemClickListener((parent, view, position, id) -> {
            selectedCourt = (Court) parent.getItemAtPosition(position);
            updateEstimate();
        });
        actCourt.setOnClickListener(v -> {
            if (!actCourt.isPopupShowing()) {
                actCourt.showDropDown();
            }
        });
        actCourt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (selectedCourt != null
                        && !s.toString().equals(selectedCourt.toString())) {
                    selectedCourt = null;
                    updateEstimate();
                }
            }
        });

        btnDate.setOnClickListener(v -> pickDate());
        btnStartTime.setOnClickListener(v -> pickTime(true));
        btnEndTime.setOnClickListener(v -> pickTime(false));
        findViewById(R.id.chipToday).setOnClickListener(v -> setQuickDate(0));
        findViewById(R.id.chipTomorrow).setOnClickListener(v -> setQuickDate(1));
        findViewById(R.id.chipDuration60).setOnClickListener(v -> setDuration(60));
        findViewById(R.id.chipDuration90).setOnClickListener(v -> setDuration(90));
        findViewById(R.id.chipDuration120).setOnClickListener(v -> setDuration(120));
        findViewById(R.id.btnPickCustomer).setOnClickListener(v -> showCustomerPicker());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());

        etCustomerPhone.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String phone = s.toString().replaceAll("[^0-9]", "");
                if (phone.length() >= 9 && phone.length() <= 11
                        && textOf(etCustomerName).isEmpty()) {
                    Customer customer = db.findCustomerByPhone(phone);
                    if (customer != null) {
                        etCustomerName.setText(customer.getName());
                    }
                }
            }
        });

        updateLabels();
        updateEstimate();
    }

    private void loadCourts() {
        courts.clear();
        courts.addAll(db.getActiveCourts());

        if (editingBooking != null) {
            Court current = db.getCourt(editingBooking.getCourtId());
            if (current != null) {
                boolean included = false;
                for (Court court : courts) {
                    if (court.getId() == current.getId()) {
                        included = true;
                        break;
                    }
                }
                if (!included) {
                    courts.add(current);
                }
                selectedCourt = current;
                actCourt.setText(current.toString(), false);
            }
        }

        ArrayAdapter<Court> courtAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, courts);
        actCourt.setAdapter(courtAdapter);
    }

    private void pickDate() {
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.choose_date);
        long selection = DateTimeUtils.toUtcDateMillis(selectedDate);
        if (selection > 0) {
            builder.setSelection(selection);
        }
        MaterialDatePicker<Long> picker = builder.build();
        picker.addOnPositiveButtonClickListener(millis -> {
            selectedDate = DateTimeUtils.fromUtcDateMillis(millis);
            updateLabels();
            updateEstimate();
        });
        picker.show(getSupportFragmentManager(), "booking_date");
    }

    private void pickTime(boolean isStart) {
        String current = isStart ? startTime : endTime;
        int totalMinutes = DateTimeUtils.minutes(current);
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(totalMinutes / 60)
                .setMinute(totalMinutes % 60)
                .setTitleText(isStart
                        ? R.string.choose_start_time : R.string.choose_end_time)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            String value = String.format(Locale.US, "%02d:%02d",
                    picker.getHour(), picker.getMinute());
            if (isStart) {
                startTime = value;
            } else {
                endTime = value;
            }
            updateLabels();
            updateEstimate();
        });
        picker.show(getSupportFragmentManager(), "booking_time");
    }

    private void setQuickDate(int daysFromToday) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, daysFromToday);
        selectedDate = DateTimeUtils.formatDbDate(calendar.getTime());
        updateLabels();
        updateEstimate();
    }

    private void setDuration(int minutes) {
        int endMinutes = DateTimeUtils.minutes(startTime) + minutes;
        if (endMinutes >= 24 * 60) {
            showError(R.string.error_quick_duration);
            return;
        }
        endTime = String.format(Locale.US, "%02d:%02d",
                endMinutes / 60, endMinutes % 60);
        updateLabels();
        updateEstimate();
    }

    private void updateLabels() {
        btnDate.setText(getString(R.string.date_prefix,
                DateTimeUtils.displayDate(selectedDate)));
        btnStartTime.setText(startTime);
        btnEndTime.setText(endTime);
    }

    private void updateEstimate() {
        int price = selectedCourt == null ? 0 : selectedCourt.getPricePerHour();
        int total = DateTimeUtils.estimateTotal(price, startTime, endTime);
        tvEstimate.setText(MoneyUtils.format(total));
    }

    private void save() {
        if (selectedCourt == null) {
            showError(R.string.error_court);
            return;
        }

        String name = textOf(etCustomerName);
        String phone = textOf(etCustomerPhone).replaceAll("[^0-9]", "");
        String note = textOf(etNote);

        if (name.isEmpty()) {
            showError(R.string.error_name);
            return;
        }
        if (phone.length() < 9 || phone.length() > 11) {
            showError(R.string.error_phone);
            return;
        }
        if (DateTimeUtils.minutes(endTime) <= DateTimeUtils.minutes(startTime)) {
            showError(R.string.error_time);
            return;
        }
        if (editingBooking == null && DateTimeUtils.isBeforeToday(selectedDate)) {
            showError(R.string.error_past);
            return;
        }
        boolean courtChanged = editingBooking == null
                || selectedCourt.getId() != editingBooking.getCourtId();
        if (!selectedCourt.isActive() && courtChanged) {
            showError(R.string.error_maintenance);
            return;
        }
        if (db.isOverlap(selectedCourt.getId(), selectedDate, startTime, endTime, bookingId)) {
            showError(R.string.error_overlap);
            return;
        }

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setCourtId(selectedCourt.getId());
        booking.setDate(selectedDate);
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setNote(note);
        booking.setCustomerName(name);
        booking.setCustomerPhone(phone);
        booking.setStatus(editingBooking == null
                ? Booking.STATUS_BOOKED : editingBooking.getStatus());

        long result = editingBooking == null
                ? db.insertBooking(booking) : db.updateBooking(booking);

        if (result == DatabaseHelper.ERROR_OVERLAP) {
            showError(R.string.error_overlap);
            return;
        }
        if (result == DatabaseHelper.ERROR_COURT_UNAVAILABLE) {
            showError(R.string.error_maintenance);
            return;
        }
        if (result == DatabaseHelper.ERROR_COURT_BUSY) {
            showError(R.string.error_court_busy);
            return;
        }
        if (result == DatabaseHelper.ERROR_SESSION_OVERLAP) {
            showError(R.string.error_session_overlap);
            return;
        }
        if (result == DatabaseHelper.ERROR_OUTSIDE_WALKIN) {
            showError(R.string.error_outside_walk_in);
            return;
        }
        if (result <= 0) {
            showError(R.string.error_generic);
            return;
        }

        if (editingBooking == null) {
            booking.setId((int) result);
        }
        if (bookingId > 0 && editingBooking != null
                && editingBooking.getCourtId() != booking.getCourtId()) {
            // Recalculate the previous court's alarm when a booking moves courts.
            LightScheduler.refreshCourt(this, editingBooking.getCourtId());
        }
        LightScheduler.scheduleForBooking(this, booking);

        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void showCustomerPicker() {
        List<Customer> customers = db.getAllCustomers();
        if (customers.isEmpty()) {
            Toast.makeText(this, R.string.empty_customers, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[customers.size()];
        for (int i = 0; i < customers.size(); i++) {
            items[i] = customers.get(i).toString();
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pick_customer_title)
                .setItems(items, (dialog, which) -> {
                    Customer customer = customers.get(which);
                    etCustomerName.setText(customer.getName());
                    etCustomerPhone.setText(customer.getPhone());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showError(int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
