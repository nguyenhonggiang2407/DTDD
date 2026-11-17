package com.example.courtmatepickleballcourtmanager;

import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.model.Event;
import com.example.courtmatepickleballcourtmanager.model.EventRegistration;
import com.example.courtmatepickleballcourtmanager.model.EventSchedule;
import com.example.courtmatepickleballcourtmanager.model.Promotion;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.example.courtmatepickleballcourtmanager.util.PromotionEngine;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class EventManagementActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private final List<Event> events = new ArrayList<>();
    private final List<Court> courts = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView tvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_list);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.event_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.listItems);
        tvInfo = findViewById(R.id.tvListInfo);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                showActions(events.get(position)));
        ExtendedFloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setText(R.string.add);
        fab.setOnClickListener(v -> showForm(null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        courts.clear();
        courts.addAll(db.getAllCourts());
        reload();
    }

    private void reload() {
        events.clear();
        events.addAll(db.getEvents());
        List<String> rows = new ArrayList<>();
        for (Event event : events) {
            String type = Event.TYPE_TOURNAMENT.equals(event.type)
                    ? getString(R.string.event_tournament) : getString(R.string.event_event);
            rows.add(event.name + " • " + type + "\n"
                    + event.startDate + " → " + event.endDate + " • "
                    + event.registered + "/" + event.capacity + " • "
                    + MoneyUtils.format(event.fee));
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(getString(R.string.result_count, events.size()));
    }

    private void showForm(Event existing) {
        Event event = existing == null ? new Event() : existing;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        EditText name = input(getString(R.string.event_name), event.name);
        Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{getString(R.string.event_event),
                        getString(R.string.event_tournament)}));
        type.setSelection(Event.TYPE_TOURNAMENT.equals(event.type) ? 1 : 0);
        EditText start = input(getString(R.string.event_start),
                event.startDate == null ? DateTimeUtils.today() : event.startDate);
        EditText end = input(getString(R.string.event_end),
                event.endDate == null ? DateTimeUtils.today() : event.endDate);
        EditText deadline = input(getString(R.string.event_deadline),
                event.deadline == null ? DateTimeUtils.today() : event.deadline);
        EditText fee = numberInput(getString(R.string.event_fee), event.fee);
        EditText capacity = numberInput(getString(R.string.event_capacity), event.capacity);
        CheckBox annual = new CheckBox(this);
        annual.setText(R.string.event_annual);
        annual.setChecked(event.annual == 1);
        layout.addView(name);
        layout.addView(type);
        layout.addView(start);
        layout.addView(end);
        layout.addView(deadline);
        layout.addView(fee);
        layout.addView(capacity);
        layout.addView(annual);

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add : R.string.event_title)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    event.name = name.getText().toString().trim();
                    event.type = type.getSelectedItemPosition() == 1
                            ? Event.TYPE_TOURNAMENT : Event.TYPE_EVENT;
                    event.startDate = start.getText().toString().trim();
                    event.endDate = end.getText().toString().trim();
                    event.deadline = deadline.getText().toString().trim();
                    event.fee = parseInt(fee, 0);
                    event.capacity = parseInt(capacity, 0);
                    event.annual = annual.isChecked() ? 1 : 0;
                    if (event.name.isEmpty()) {
                        return;
                    }
                    if (existing == null) {
                        event.status = Event.STATUS_UPCOMING;
                        db.insertEvent(event);
                    } else {
                        db.updateEvent(event);
                    }
                    Toast.makeText(this, R.string.event_saved, Toast.LENGTH_SHORT).show();
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showActions(Event event) {
        String[] actions = {
                getString(R.string.event_schedule),
                getString(R.string.event_register),
                getString(R.string.event_registrations),
                getString(R.string.event_duplicate),
                getString(R.string.delete_item),
                getString(R.string.close)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(event.name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        manageSchedules(event);
                    } else if (which == 1) {
                        register(event);
                    } else if (which == 2) {
                        showRegistrations(event);
                    } else if (which == 3) {
                        long id = db.duplicateEventToNextYear(event.id);
                        if (id > 0) {
                            Toast.makeText(this, R.string.event_duplicated,
                                    Toast.LENGTH_SHORT).show();
                            reload();
                        }
                    } else if (which == 4) {
                        confirmDelete(event);
                    }
                })
                .show();
    }

    private void manageSchedules(Event event) {
        List<EventSchedule> schedules = db.getEventSchedules(event.id);
        StringBuilder message = new StringBuilder();
        for (EventSchedule schedule : schedules) {
            message.append("• ").append(schedule.label()).append("\n");
        }
        if (message.length() == 0) {
            message.append(getString(R.string.stats_no_data));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.event_schedule)
                .setMessage(message.toString().trim())
                .setPositiveButton(R.string.event_add_schedule, (dialog, which) ->
                        addSchedule(event, schedules))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void addSchedule(Event event, List<EventSchedule> schedules) {
        if (courts.isEmpty()) {
            Toast.makeText(this, R.string.empty_courts, Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        Spinner court = new Spinner(this);
        List<String> courtNames = new ArrayList<>();
        for (Court value : courts) {
            courtNames.add(value.getName());
        }
        court.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, courtNames));
        EditText date = input(getString(R.string.choose_date),
                event.startDate == null ? DateTimeUtils.today() : event.startDate);
        EditText start = input(getString(R.string.walk_in_start), "08:00");
        EditText end = input(getString(R.string.walk_in_end), "11:00");
        EditText content = input(getString(R.string.event_schedule_content), "");
        layout.addView(court);
        layout.addView(date);
        layout.addView(start);
        layout.addView(end);
        layout.addView(content);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.event_add_schedule)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    EventSchedule schedule = new EventSchedule();
                    schedule.eventId = event.id;
                    schedule.courtId = courts.get(court.getSelectedItemPosition()).getId();
                    schedule.date = date.getText().toString().trim();
                    schedule.startTime = start.getText().toString().trim();
                    schedule.endTime = end.getText().toString().trim();
                    schedule.content = content.getText().toString().trim();
                    if (schedule.endTime.compareTo(schedule.startTime) <= 0) {
                        Toast.makeText(this, R.string.error_time, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    schedules.add(schedule);
                    db.saveEventSchedules(event.id, schedules);
                    List<Integer> courtIds = new ArrayList<>();
                    for (EventSchedule value : schedules) {
                        if (!courtIds.contains(value.courtId)) {
                            courtIds.add(value.courtId);
                        }
                    }
                    db.saveEventCourts(event.id, courtIds);
                    Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void register(Event event) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        EditText name = input(getString(R.string.customer_name_hint), "");
        EditText phone = input(getString(R.string.phone_hint), "");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText code = input(getString(R.string.promotion_code_hint), "");
        layout.addView(name);
        layout.addView(phone);
        layout.addView(code);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.event_register)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String today = DateTimeUtils.today();
                    List<Court> eventCourts = db.getEventCourts(event.id);
                    int courtId = eventCourts.isEmpty() ? 0 : eventCourts.get(0).getId();
                    List<Promotion> automatic = db.getAutomaticPromotions(today, courtId);
                    Promotion codePromotion = db.findPromotionByCode(
                            code.getText().toString().trim());
                    PromotionEngine.Result discount = PromotionEngine.bestForScope(
                            automatic, Promotion.SCOPE_EVENT, event.fee, today,
                            codePromotion, code.getText().toString().trim());
                    long id = db.registerForEvent(event.id, name.getText().toString(),
                            phone.getText().toString(), discount.totalDiscount,
                            discount.promotion == null ? 0 : discount.promotion.id);
                    if (id > 0) {
                        Toast.makeText(this, discount.hasDiscount()
                                        ? getString(R.string.event_registered_discount,
                                        MoneyUtils.format(discount.totalDiscount))
                                        : getString(R.string.event_registered),
                                Toast.LENGTH_SHORT).show();
                        reload();
                    } else if (id == DatabaseHelper.ERROR_CAPACITY) {
                        Toast.makeText(this, R.string.class_full, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showRegistrations(Event event) {
        List<EventRegistration> registrations = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (EventRegistration registration : db.getEventRegistrations(event.id)) {
            if (EventRegistration.STATUS_CANCELLED.equals(registration.status)) {
                continue;
            }
            registrations.add(registration);
            labels.add(registration.customerName + " • " + registration.customerPhone + " • "
                    + MoneyUtils.format(registration.fee - registration.discount)
                    + (registration.paid == 1 ? " • đã thu" : " • chưa thu"));
        }
        if (registrations.isEmpty()) {
            Toast.makeText(this, R.string.stats_no_data, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.event_registrations)
                .setItems(labels.toArray(new String[0]), (dialog, which) ->
                        registrationActions(event, registrations.get(which)))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void registrationActions(Event event, EventRegistration registration) {
        String[] actions = {
                getString(registration.paid == 1 ? R.string.mark_unpaid : R.string.mark_paid),
                getString(R.string.cancel_enrollment),
                getString(R.string.close)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(registration.customerName)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        db.markEventRegistrationPaid(registration.id,
                                registration.paid == 1 ? 0 : 1);
                        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                        showRegistrations(event);
                    } else if (which == 1) {
                        db.cancelEventRegistration(registration.id);
                        Toast.makeText(this, R.string.toast_cancelled,
                                Toast.LENGTH_SHORT).show();
                        showRegistrations(event);
                    }
                })
                .show();
    }

    private void confirmDelete(Event event) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.event_delete_confirm, event.name))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    db.deleteEvent(event.id);
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private EditText input(String hint, String value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        if (value != null) {
            editText.setText(value);
        }
        return editText;
    }

    private EditText numberInput(String hint, int value) {
        EditText editText = input(hint, String.valueOf(value));
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        return editText;
    }

    private int parseInt(EditText editText, int fallback) {
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
