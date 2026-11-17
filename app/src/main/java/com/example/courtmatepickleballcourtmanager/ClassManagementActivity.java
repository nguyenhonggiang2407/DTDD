package com.example.courtmatepickleballcourtmanager;

import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.ClassEnrollment;
import com.example.courtmatepickleballcourtmanager.model.ClassRoom;
import com.example.courtmatepickleballcourtmanager.model.ClassSchedule;
import com.example.courtmatepickleballcourtmanager.model.Coach;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.model.Promotion;
import com.example.courtmatepickleballcourtmanager.model.WalkInSlot;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.example.courtmatepickleballcourtmanager.util.PromotionEngine;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ClassManagementActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private final List<ClassRoom> classes = new ArrayList<>();
    private final List<Coach> coaches = new ArrayList<>();
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
        setTitle(R.string.class_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.listItems);
        tvInfo = findViewById(R.id.tvListInfo);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                showActions(classes.get(position)));
        ExtendedFloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setText(R.string.add);
        fab.setOnClickListener(v -> showForm(null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        coaches.clear();
        coaches.addAll(db.getCoaches());
        courts.clear();
        courts.addAll(db.getAllCourts());
        reload();
    }

    private void reload() {
        classes.clear();
        classes.addAll(db.getClasses());
        List<String> rows = new ArrayList<>();
        for (ClassRoom room : classes) {
            String level = room.level == null || room.level.isEmpty() ? "" : room.level + " • ";
            rows.add(room.name + "\n" + level + "HLV " + room.coachName + " • "
                    + room.courtName + " • " + room.enrolled + "/" + room.capacity
                    + " • " + MoneyUtils.format(room.fee));
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(getString(R.string.result_count, classes.size()));
    }

    private void showForm(ClassRoom existing) {
        if (coaches.isEmpty() || courts.isEmpty()) {
            Toast.makeText(this, R.string.stats_no_data, Toast.LENGTH_SHORT).show();
            return;
        }
        ClassRoom room = existing == null ? new ClassRoom() : existing;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        EditText name = input(getString(R.string.class_name), room.name);
        EditText level = input(getString(R.string.class_level), room.level);
        Spinner coach = new Spinner(this);
        List<String> coachNames = new ArrayList<>();
        int coachSelection = 0;
        for (int i = 0; i < coaches.size(); i++) {
            coachNames.add(coaches.get(i).name);
            if (coaches.get(i).id == room.coachId) {
                coachSelection = i;
            }
        }
        coach.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, coachNames));
        coach.setSelection(coachSelection);
        Spinner court = new Spinner(this);
        List<String> courtNames = new ArrayList<>();
        int courtSelection = 0;
        for (int i = 0; i < courts.size(); i++) {
            courtNames.add(courts.get(i).getName());
            if (courts.get(i).getId() == room.courtId) {
                courtSelection = i;
            }
        }
        court.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, courtNames));
        court.setSelection(courtSelection);
        EditText capacity = numberInput(getString(R.string.class_capacity),
                room.capacity <= 0 ? 12 : room.capacity);
        EditText fee = numberInput(getString(R.string.class_fee), room.fee);
        EditText start = input(getString(R.string.class_start),
                room.startDate == null ? DateTimeUtils.today() : room.startDate);
        EditText end = input(getString(R.string.class_end),
                room.endDate == null ? DateTimeUtils.shiftYears(DateTimeUtils.today(), 1)
                        : room.endDate);
        layout.addView(name);
        layout.addView(level);
        layout.addView(label(R.string.management_coaches));
        layout.addView(coach);
        layout.addView(label(R.string.court_hint));
        layout.addView(court);
        layout.addView(capacity);
        layout.addView(fee);
        layout.addView(start);
        layout.addView(end);

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add : R.string.class_title)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    room.name = name.getText().toString().trim();
                    room.level = level.getText().toString().trim();
                    room.coachId = coaches.get(coach.getSelectedItemPosition()).id;
                    room.courtId = courts.get(court.getSelectedItemPosition()).getId();
                    room.capacity = parseInt(capacity, 1);
                    room.fee = parseInt(fee, 0);
                    room.startDate = start.getText().toString().trim();
                    room.endDate = end.getText().toString().trim();
                    if (room.name.isEmpty() || room.capacity <= 0) {
                        return;
                    }
                    if (existing == null) {
                        room.status = ClassRoom.STATUS_OPEN;
                        db.insertClassRoom(room);
                    } else {
                        db.updateClassRoom(room);
                    }
                    Toast.makeText(this, R.string.class_saved, Toast.LENGTH_SHORT).show();
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showActions(ClassRoom room) {
        String[] actions = {
                getString(R.string.class_schedule),
                getString(R.string.class_enroll),
                getString(R.string.class_enrollments),
                getString(R.string.delete_item),
                getString(R.string.close)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(room.name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        manageSchedules(room);
                    } else if (which == 1) {
                        enroll(room);
                    } else if (which == 2) {
                        showEnrollments(room);
                    } else if (which == 3) {
                        confirmDelete(room);
                    }
                })
                .show();
    }

    private void manageSchedules(ClassRoom room) {
        List<ClassSchedule> schedules = db.getClassSchedules(room.id);
        StringBuilder message = new StringBuilder();
        for (ClassSchedule schedule : schedules) {
            message.append("• ").append(schedule.label()).append("\n");
        }
        if (message.length() == 0) {
            message.append(getString(R.string.stats_no_data));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.class_schedule)
                .setMessage(message.toString().trim())
                .setPositiveButton(R.string.class_add_schedule, (dialog, which) ->
                        addSchedule(room, schedules))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void addSchedule(ClassRoom room, List<ClassSchedule> schedules) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        Spinner weekday = new Spinner(this);
        List<String> weekdays = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            weekdays.add(WalkInSlot.weekdayLabel(i));
        }
        weekday.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, weekdays));
        EditText start = input(getString(R.string.walk_in_start), "18:00");
        EditText end = input(getString(R.string.walk_in_end), "19:30");
        layout.addView(weekday);
        layout.addView(start);
        layout.addView(end);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.class_add_schedule)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    ClassSchedule schedule = new ClassSchedule();
                    schedule.classId = room.id;
                    schedule.weekday = weekday.getSelectedItemPosition() + 1;
                    schedule.startTime = start.getText().toString().trim();
                    schedule.endTime = end.getText().toString().trim();
                    if (schedule.endTime.compareTo(schedule.startTime) <= 0) {
                        Toast.makeText(this, R.string.error_time, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    schedules.add(schedule);
                    db.saveClassSchedules(room.id, schedules);
                    Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void enroll(ClassRoom room) {
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
                .setTitle(R.string.class_enroll)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String today = DateTimeUtils.today();
                    List<Promotion> automatic = db.getAutomaticPromotions(today, room.courtId);
                    Promotion codePromotion = db.findPromotionByCode(
                            code.getText().toString().trim());
                    PromotionEngine.Result discount = PromotionEngine.bestForScope(
                            automatic, Promotion.SCOPE_CLASS, room.fee, today,
                            codePromotion, code.getText().toString().trim());
                    long id = db.registerForClass(room.id, name.getText().toString(),
                            phone.getText().toString(), discount.totalDiscount,
                            discount.promotion == null ? 0 : discount.promotion.id);
                    if (id > 0) {
                        Toast.makeText(this, discount.hasDiscount()
                                        ? getString(R.string.class_enrolled_discount,
                                        MoneyUtils.format(discount.totalDiscount))
                                        : getString(R.string.class_enrolled),
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

    private void showEnrollments(ClassRoom room) {
        List<ClassEnrollment> enrollments = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (ClassEnrollment enrollment : db.getClassEnrollments(room.id)) {
            if (ClassEnrollment.STATUS_CANCELLED.equals(enrollment.status)) {
                continue;
            }
            enrollments.add(enrollment);
            labels.add(enrollment.customerName + " • " + enrollment.customerPhone + " • "
                    + MoneyUtils.format(enrollment.fee - enrollment.discount)
                    + (enrollment.paid == 1 ? " • đã thu" : " • chưa thu"));
        }
        if (enrollments.isEmpty()) {
            Toast.makeText(this, R.string.stats_no_data, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.class_enrollments)
                .setItems(labels.toArray(new String[0]), (dialog, which) ->
                        enrollmentActions(room, enrollments.get(which)))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void enrollmentActions(ClassRoom room, ClassEnrollment enrollment) {
        String[] actions = {
                getString(enrollment.paid == 1 ? R.string.mark_unpaid : R.string.mark_paid),
                getString(R.string.cancel_enrollment),
                getString(R.string.close)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(enrollment.customerName)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        db.markClassEnrollmentPaid(enrollment.id, enrollment.paid == 1 ? 0 : 1);
                        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                        showEnrollments(room);
                    } else if (which == 1) {
                        db.cancelClassEnrollment(enrollment.id);
                        Toast.makeText(this, R.string.toast_cancelled,
                                Toast.LENGTH_SHORT).show();
                        showEnrollments(room);
                    }
                })
                .show();
    }

    private void confirmDelete(ClassRoom room) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.class_delete_confirm, room.name))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    db.deleteClassRoom(room.id);
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

    private TextView label(int text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setPadding(0, dp(10), 0, 0);
        return view;
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
