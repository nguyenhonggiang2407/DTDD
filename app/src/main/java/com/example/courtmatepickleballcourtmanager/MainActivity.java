package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.courtmatepickleballcourtmanager.adapter.BookingAdapter;
import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.Booking;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.LightScheduler;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends ProtectedActivity
        implements BookingAdapter.OnBookingClickListener {

    private DatabaseHelper db;
    private BookingAdapter adapter;
    private final List<Booking> bookings = new ArrayList<>();

    private TextInputEditText etSearch;
    private MaterialButton btnFilterDate;
    private MaterialButton btnClearDate;
    private MaterialAutoCompleteTextView actCourtFilter;
    private ChipGroup chipGroupStatus;
    private RecyclerView rvBookings;
    private View layoutEmpty;
    private TextView tvEmptyBookingsTitle;
    private TextView tvEmptyBookingsHint;
    private TextView tvResultCount;
    private SwipeRefreshLayout swipeRefresh;
    private String filterDate;
    private String filterStatus;
    private int filterCourtId;
    private boolean onlyUpcoming;
    private int minAmount;
    private int maxAmount;
    private int sortMode;
    private final List<Court> filterCourts = new ArrayList<>();
    private ActivityResultLauncher<String> exportLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"),
                uri -> {
                    if (uri != null) {
                        exportBookings(uri);
                    }
                });
        setContentView(R.layout.activity_main);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.title_bookings);
        toolbar.setSubtitle(R.string.app_title_short);
        toolbar.setNavigationIcon(null);

        etSearch = findViewById(R.id.etSearch);
        btnFilterDate = findViewById(R.id.btnFilterDate);
        btnClearDate = findViewById(R.id.btnClearDate);
        actCourtFilter = findViewById(R.id.actCourtFilter);
        chipGroupStatus = findViewById(R.id.chipGroupStatus);
        rvBookings = findViewById(R.id.rvBookings);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvEmptyBookingsTitle = findViewById(R.id.tvEmptyBookingsTitle);
        tvEmptyBookingsHint = findViewById(R.id.tvEmptyBookingsHint);
        tvResultCount = findViewById(R.id.tvResultCount);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        adapter = new BookingAdapter(bookings, this);
        rvBookings.setLayoutManager(new LinearLayoutManager(this));
        rvBookings.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateFilterUi();
                reload();
            }
        });
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(v);
                reload();
                return true;
            }
            return false;
        });

        btnFilterDate.setOnClickListener(v -> openDatePicker());
        btnClearDate.setOnClickListener(v -> {
            hideKeyboard(etSearch);
            etSearch.setText("");
            filterDate = null;
            filterCourtId = 0;
            filterStatus = null;
            onlyUpcoming = false;
            minAmount = 0;
            maxAmount = 0;
            sortMode = 0;
            actCourtFilter.setText(getString(R.string.all_courts), false);
            if (chipGroupStatus.getCheckedChipId() != R.id.chipAll) {
                chipGroupStatus.check(R.id.chipAll);
            }
            updateFilterUi();
            reload();
        });

        chipGroupStatus.check(R.id.chipAll);
        chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checkedId = checkedIds.isEmpty() ? R.id.chipAll : checkedIds.get(0);
            if (checkedId == R.id.chipBooked) {
                filterStatus = Booking.STATUS_BOOKED;
            } else if (checkedId == R.id.chipCancelled) {
                filterStatus = Booking.STATUS_CANCELLED;
            } else {
                filterStatus = null;
            }
            updateFilterUi();
            reload();
        });

        setupCourtFilter();

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                setupCourtFilter();
                reload();
                swipeRefresh.setRefreshing(false);
            });
        }

        findViewById(R.id.btnAdvancedSearch).setOnClickListener(v -> showAdvancedSearch());

        findViewById(R.id.fabAddBooking).setOnClickListener(v ->
                startActivity(new Intent(this, BookingFormActivity.class)));

        updateFilterUi();
    }

    private void setupCourtFilter() {
        int selectedCourtId = filterCourtId;
        filterCourts.clear();
        filterCourts.addAll(db.getAllCourts());

        List<String> names = new ArrayList<>();
        names.add(getString(R.string.all_courts));
        for (Court court : filterCourts) {
            names.add(court.getName());
        }

        ArrayAdapter<String> courtAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, names);
        actCourtFilter.setAdapter(courtAdapter);

        int selection = 0;
        for (int i = 0; i < filterCourts.size(); i++) {
            if (filterCourts.get(i).getId() == selectedCourtId) {
                selection = i + 1;
                break;
            }
        }
        filterCourtId = selection == 0 ? 0 : filterCourts.get(selection - 1).getId();
        actCourtFilter.setText(names.get(selection), false);

        actCourtFilter.setOnItemClickListener((parent, view, position, id) -> {
            filterCourtId = position == 0 ? 0 : filterCourts.get(position - 1).getId();
            updateFilterUi();
            reload();
        });
        actCourtFilter.setOnClickListener(v -> {
            if (!actCourtFilter.isPopupShowing()) {
                actCourtFilter.showDropDown();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupCourtFilter();
        reload();
    }

    private void reload() {
        String keyword = etSearch.getText() == null ? "" : etSearch.getText().toString();
        List<Booking> result = new ArrayList<>();
        String today = DateTimeUtils.today();
        for (Booking booking : db.searchBookings(keyword, filterDate, filterCourtId,
                filterStatus)) {
            if (onlyUpcoming && booking.getDate().compareTo(today) < 0) {
                continue;
            }
            if (minAmount > 0 && booking.getTotalAmount() < minAmount) {
                continue;
            }
            if (maxAmount > 0 && booking.getTotalAmount() > maxAmount) {
                continue;
            }
            result.add(booking);
        }
        if (sortMode == 1) {
            Collections.sort(result, (a, b) ->
                    Integer.compare(b.getTotalAmount(), a.getTotalAmount()));
        } else if (sortMode == 2) {
            Collections.sort(result, (a, b) ->
                    Integer.compare(a.getTotalAmount(), b.getTotalAmount()));
        } else if (sortMode == 3) {
            Collections.sort(result, (a, b) -> {
                int date = a.getDate().compareTo(b.getDate());
                return date != 0 ? date : a.getStartTime().compareTo(b.getStartTime());
            });
        }
        adapter.replaceAll(result);

        boolean empty = result.isEmpty();
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvBookings.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvResultCount.setText(getString(R.string.result_count, result.size()));

        boolean searching = !keyword.trim().isEmpty()
                || filterDate != null || filterCourtId > 0 || filterStatus != null
                || onlyUpcoming || minAmount > 0 || maxAmount > 0 || sortMode > 0;
        tvEmptyBookingsTitle.setText(searching
                ? R.string.empty_search_result : R.string.empty_bookings);
        tvEmptyBookingsHint.setText(searching
                ? R.string.empty_search_hint : R.string.empty_bookings_hint);
    }

    private void openDatePicker() {
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.choose_date);
        long selection = DateTimeUtils.toUtcDateMillis(filterDate);
        if (selection > 0) {
            builder.setSelection(selection);
        }
        MaterialDatePicker<Long> picker = builder.build();
        picker.addOnPositiveButtonClickListener(millis -> {
            filterDate = DateTimeUtils.fromUtcDateMillis(millis);
            updateFilterUi();
            reload();
        });
        picker.show(getSupportFragmentManager(), "filter_date");
    }

    private void updateFilterUi() {
        if (filterDate == null) {
            btnFilterDate.setText(R.string.all_dates);
        } else {
            btnFilterDate.setText(getString(R.string.date_prefix,
                    DateTimeUtils.displayDate(filterDate)));
        }
        String keyword = etSearch.getText() == null
                ? "" : etSearch.getText().toString().trim();
        boolean filtering = !keyword.isEmpty() || filterDate != null
                || filterCourtId != 0 || filterStatus != null
                || onlyUpcoming || minAmount > 0 || maxAmount > 0 || sortMode > 0;
        btnClearDate.setVisibility(filtering ? View.VISIBLE : View.GONE);
    }

    private void showAdvancedSearch() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, 0);

        CheckBox upcoming = new CheckBox(this);
        upcoming.setText(R.string.only_upcoming);
        upcoming.setChecked(onlyUpcoming);
        EditText min = amountInput(R.string.min_amount, minAmount);
        EditText max = amountInput(R.string.max_amount, maxAmount);
        Spinner sort = new Spinner(this);
        String[] sortLabels = {
                getString(R.string.sort_default),
                getString(R.string.sort_amount_desc),
                getString(R.string.sort_amount_asc),
                getString(R.string.sort_date_asc)
        };
        sort.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, sortLabels));
        sort.setSelection(Math.max(0, Math.min(sortMode, sortLabels.length - 1)));
        layout.addView(upcoming);
        layout.addView(min);
        layout.addView(max);
        layout.addView(sort);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.advanced_search)
                .setView(layout)
                .setPositiveButton(R.string.apply, null)
                .setNeutralButton(R.string.reset, (d, which) -> {
                    onlyUpcoming = false;
                    minAmount = 0;
                    maxAmount = 0;
                    sortMode = 0;
                    updateFilterUi();
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int minValue = parseAmount(min);
                    int maxValue = parseAmount(max);
                    if (maxValue > 0 && minValue > maxValue) {
                        Toast.makeText(this, R.string.error_price_range,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    onlyUpcoming = upcoming.isChecked();
                    minAmount = minValue;
                    maxAmount = maxValue;
                    sortMode = sort.getSelectedItemPosition();
                    updateFilterUi();
                    reload();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private EditText amountInput(int hint, int value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (value > 0) {
            editText.setText(String.valueOf(value));
        }
        return editText;
    }

    private int parseAmount(EditText editText) {
        try {
            return Math.max(0, Integer.parseInt(
                    editText.getText().toString().trim().replace(".", "")));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        view.clearFocus();
    }

    @Override
    public void onBookingClick(Booking booking) {
        List<String> actions = new ArrayList<>();
        if (booking.isCancelled()) {
            actions.add(getString(R.string.delete_booking));
            actions.add(getString(R.string.duplicate_booking));
        } else {
            actions.add(getString(R.string.edit_booking));
            actions.add(getString(R.string.cancel_booking));
            actions.add(getString(R.string.duplicate_booking));
        }
        actions.add(getString(R.string.close));

        new MaterialAlertDialogBuilder(this)
                .setTitle(booking.getCourtName())
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if (action.equals(getString(R.string.edit_booking))) {
                        Intent intent = new Intent(this, BookingFormActivity.class);
                        intent.putExtra(BookingFormActivity.EXTRA_BOOKING_ID, booking.getId());
                        startActivity(intent);
                    } else if (action.equals(getString(R.string.cancel_booking))) {
                        confirmCancel(booking);
                    } else if (action.equals(getString(R.string.delete_booking))) {
                        confirmDelete(booking);
                    } else if (action.equals(getString(R.string.duplicate_booking))) {
                        Intent intent = new Intent(this, BookingFormActivity.class);
                        intent.putExtra(BookingFormActivity.EXTRA_DUPLICATE_FROM, booking.getId());
                        startActivity(intent);
                        Toast.makeText(this, R.string.toast_duplicated, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void confirmCancel(Booking booking) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_cancel_title)
                .setMessage(getString(R.string.confirm_cancel_message,
                        booking.getCustomerName(),
                        booking.getStartTime(),
                        DateTimeUtils.displayDate(booking.getDate())))
                .setPositiveButton(R.string.confirm_yes, (dialog, which) -> {
                    if (db.cancelBooking(booking.getId()) > 0) {
                        LightScheduler.refreshCourt(this, booking.getCourtId());
                        Toast.makeText(this, R.string.toast_cancelled, Toast.LENGTH_SHORT).show();
                        reload();
                    }
                })
                .setNegativeButton(R.string.confirm_no, null)
                .show();
    }

    private void confirmDelete(Booking booking) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_delete_booking_title)
                .setMessage(R.string.confirm_delete_booking_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (db.deleteBooking(booking.getId()) > 0) {
                        LightScheduler.refreshCourt(this, booking.getCourtId());
                        Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
                        reload();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void exportBookings(Uri uri) {
        List<Booking> all = db.searchBookings("", null, 0, null);
        try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
            if (stream == null) {
                throw new IOException("Cannot open output stream");
            }
            stream.write(0xEF);
            stream.write(0xBB);
            stream.write(0xBF);
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
                writer.write("Mã,Sân,Khách hàng,Số điện thoại,Ngày,"
                        + "Giờ bắt đầu,Giờ kết thúc,Tổng tiền (VNĐ),Trạng thái,Ghi chú");
                writer.newLine();
                for (Booking booking : all) {
                    writer.write(csvValue(String.valueOf(booking.getId())));
                    writer.write(',');
                    writer.write(csvValue(booking.getCourtName()));
                    writer.write(',');
                    writer.write(csvValue(booking.getCustomerName()));
                    writer.write(',');
                    writer.write(csvValue(booking.getCustomerPhone()));
                    writer.write(',');
                    writer.write(csvValue(DateTimeUtils.displayDate(booking.getDate())));
                    writer.write(',');
                    writer.write(csvValue(booking.getStartTime()));
                    writer.write(',');
                    writer.write(csvValue(booking.getEndTime()));
                    writer.write(',');
                    writer.write(csvValue(String.valueOf(booking.getTotalAmount())));
                    writer.write(',');
                    writer.write(csvValue(getString(booking.isCancelled()
                            ? R.string.status_cancelled : R.string.status_booked)));
                    writer.write(',');
                    writer.write(csvValue(booking.getNote()));
                    writer.newLine();
                }
            }
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show();
        } catch (IOException | SecurityException e) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private String csvValue(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
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
        if (id == R.id.action_calendar) {
            startActivity(new Intent(this, CalendarViewActivity.class));
            return true;
        }
        if (id == R.id.action_export) {
            exportLauncher.launch(getString(R.string.export_file_name,
                    DateTimeUtils.today().replace("-", "")));
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
