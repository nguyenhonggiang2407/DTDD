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
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.model.WalkInSlot;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class WalkInSlotActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private final List<WalkInSlot> slots = new ArrayList<>();
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
        setTitle(R.string.walk_in_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.listItems);
        tvInfo = findViewById(R.id.tvListInfo);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                showForm(slots.get(position)));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDelete(slots.get(position));
            return true;
        });
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
        slots.clear();
        slots.addAll(db.getWalkInSlots());
        List<String> rows = new ArrayList<>();
        for (WalkInSlot slot : slots) {
            rows.add(slot.courtName + " • " + slot.weekdayLabel()
                    + "\n" + slot.startTime + "–" + slot.endTime + " • "
                    + MoneyUtils.format(slot.price) + "/giờ");
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(getString(R.string.result_count, slots.size()));
    }

    private void showForm(WalkInSlot existing) {
        WalkInSlot slot = existing == null ? new WalkInSlot() : existing;
        if (courts.isEmpty()) {
            Toast.makeText(this, R.string.empty_courts, Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);

        Spinner court = new Spinner(this);
        List<String> courtNames = new ArrayList<>();
        int courtSelection = 0;
        for (int i = 0; i < courts.size(); i++) {
            courtNames.add(courts.get(i).getName());
            if (courts.get(i).getId() == slot.courtId) {
                courtSelection = i;
            }
        }
        court.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, courtNames));
        court.setSelection(courtSelection);

        Spinner weekday = new Spinner(this);
        List<String> weekdays = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            weekdays.add(WalkInSlot.weekdayLabel(i));
        }
        weekday.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, weekdays));
        weekday.setSelection(Math.max(0, slot.weekday - 1));

        EditText start = input(getString(R.string.walk_in_start),
                slot.startTime == null ? "17:00" : slot.startTime);
        EditText end = input(getString(R.string.walk_in_end),
                slot.endTime == null ? "22:00" : slot.endTime);
        EditText price = input(getString(R.string.walk_in_price),
                String.valueOf(slot.price));
        price.setInputType(InputType.TYPE_CLASS_NUMBER);

        layout.addView(label(R.string.court_hint));
        layout.addView(court);
        layout.addView(label(R.string.walk_in_weekday));
        layout.addView(weekday);
        layout.addView(start);
        layout.addView(end);
        layout.addView(price);

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add : R.string.walk_in_title)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    slot.courtId = courts.get(court.getSelectedItemPosition()).getId();
                    slot.weekday = weekday.getSelectedItemPosition() + 1;
                    slot.startTime = start.getText().toString().trim();
                    slot.endTime = end.getText().toString().trim();
                    slot.price = parseInt(price, 0);
                    if (slot.startTime.isEmpty() || slot.endTime.isEmpty()
                            || slot.endTime.compareTo(slot.startTime) <= 0) {
                        Toast.makeText(this, R.string.error_time, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long result = existing == null ? db.insertWalkInSlot(slot)
                            : db.updateWalkInSlot(slot);
                    if (result == DatabaseHelper.ERROR_OVERLAP) {
                        Toast.makeText(this, R.string.error_overlap, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, R.string.walk_in_saved, Toast.LENGTH_SHORT).show();
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(WalkInSlot slot) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(R.string.walk_in_delete_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    db.deleteWalkInSlot(slot.id);
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private EditText input(String hint, String value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value);
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
