package com.example.courtmatepickleballcourtmanager;

import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.Coach;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class CoachManagementActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private final List<Coach> coaches = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView tvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_list);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.coach_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.listItems);
        tvInfo = findViewById(R.id.tvListInfo);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                showForm(coaches.get(position)));
        ExtendedFloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setText(R.string.add);
        fab.setOnClickListener(v -> showForm(null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        coaches.clear();
        coaches.addAll(db.getCoaches());
        List<String> rows = new ArrayList<>();
        for (Coach coach : coaches) {
            rows.add(coach.name + " • " + MoneyUtils.format(coach.hourlyRate) + "/giờ\n"
                    + (coach.specialty == null ? "" : coach.specialty)
                    + (coach.phone == null ? "" : " • " + coach.phone));
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(getString(R.string.result_count, coaches.size()));
    }

    private void showForm(Coach existing) {
        Coach coach = existing == null ? new Coach() : existing;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        EditText name = input(getString(R.string.coach_name), coach.name);
        EditText phone = input(getString(R.string.coach_phone), coach.phone);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText specialty = input(getString(R.string.coach_specialty), coach.specialty);
        EditText rate = input(getString(R.string.coach_rate),
                String.valueOf(coach.hourlyRate));
        rate.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(name);
        layout.addView(phone);
        layout.addView(specialty);
        layout.addView(rate);

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add : R.string.coach_title)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    coach.name = name.getText().toString().trim();
                    coach.phone = phone.getText().toString().trim();
                    coach.specialty = specialty.getText().toString().trim();
                    coach.hourlyRate = parseInt(rate, 0);
                    if (coach.name.isEmpty()) {
                        return;
                    }
                    if (existing == null) {
                        db.insertCoach(coach);
                    } else {
                        db.updateCoach(coach);
                    }
                    Toast.makeText(this, R.string.coach_saved, Toast.LENGTH_SHORT).show();
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
