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
import com.example.courtmatepickleballcourtmanager.model.SpecialDate;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class SpecialDateActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private final List<SpecialDate> dates = new ArrayList<>();
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
        setTitle(R.string.special_date_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.listItems);
        tvInfo = findViewById(R.id.tvListInfo);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                showForm(dates.get(position)));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDelete(dates.get(position));
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
        dates.clear();
        dates.addAll(db.getSpecialDates());
        List<String> rows = new ArrayList<>();
        for (SpecialDate date : dates) {
            String type = SpecialDate.TYPE_HOLIDAY.equals(date.type)
                    ? getString(R.string.special_date_holiday)
                    : getString(R.string.special_date_anniversary);
            String suffix = date.courtName == null ? "" : " • " + date.courtName;
            rows.add(date.name + " • " + type + "\n" + date.day + "/" + date.month
                    + (date.annual == 1 ? " hằng năm" : " • " + date.year) + suffix);
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(getString(R.string.result_count, dates.size()));
    }

    private void showForm(SpecialDate existing) {
        SpecialDate date = existing == null ? new SpecialDate() : existing;
        Calendar now = Calendar.getInstance();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        EditText name = input(getString(R.string.special_date_name), date.name);
        Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{getString(R.string.special_date_holiday),
                        getString(R.string.special_date_anniversary)}));
        type.setSelection(SpecialDate.TYPE_ANNIVERSARY.equals(date.type) ? 1 : 0);
        EditText day = numberInput(getString(R.string.special_date_day),
                date.day <= 0 ? now.get(Calendar.DAY_OF_MONTH) : date.day);
        EditText month = numberInput(getString(R.string.special_date_month),
                date.month <= 0 ? now.get(Calendar.MONTH) + 1 : date.month);
        EditText year = numberInput(getString(R.string.special_date_year),
                date.year <= 0 ? now.get(Calendar.YEAR) : date.year);
        CheckBox annual = new CheckBox(this);
        annual.setText(R.string.special_date_annual);
        annual.setChecked(date.annual != 0);
        Spinner court = new Spinner(this);
        List<String> courtNames = new ArrayList<>();
        courtNames.add(getString(R.string.all_courts));
        int courtSelection = 0;
        for (int i = 0; i < courts.size(); i++) {
            courtNames.add(courts.get(i).getName());
            if (courts.get(i).getId() == date.courtId) {
                courtSelection = i + 1;
            }
        }
        court.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, courtNames));
        court.setSelection(courtSelection);

        layout.addView(name);
        layout.addView(type);
        layout.addView(day);
        layout.addView(month);
        layout.addView(year);
        layout.addView(annual);
        layout.addView(court);

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add : R.string.special_date_title)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    date.name = name.getText().toString().trim();
                    date.type = type.getSelectedItemPosition() == 1
                            ? SpecialDate.TYPE_ANNIVERSARY : SpecialDate.TYPE_HOLIDAY;
                    date.day = parseInt(day, 1);
                    date.month = parseInt(month, 1);
                    date.year = parseInt(year, now.get(Calendar.YEAR));
                    date.annual = annual.isChecked() ? 1 : 0;
                    date.courtId = court.getSelectedItemPosition() == 0 ? 0
                            : courts.get(court.getSelectedItemPosition() - 1).getId();
                    if (date.name.isEmpty() || date.day < 1 || date.day > 31
                            || date.month < 1 || date.month > 12) {
                        return;
                    }
                    if (existing == null) {
                        date.status = SpecialDate.STATUS_ACTIVE;
                        db.insertSpecialDate(date);
                    } else {
                        db.updateSpecialDate(date);
                    }
                    Toast.makeText(this, R.string.special_date_saved,
                            Toast.LENGTH_SHORT).show();
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(SpecialDate date) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.special_date_delete_confirm, date.name))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    db.deleteSpecialDate(date.id);
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
