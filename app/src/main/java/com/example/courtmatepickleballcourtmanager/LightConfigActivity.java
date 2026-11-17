package com.example.courtmatepickleballcourtmanager;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
import com.example.courtmatepickleballcourtmanager.model.LightConfig;
import com.example.courtmatepickleballcourtmanager.util.LightController;
import com.example.courtmatepickleballcourtmanager.util.LightScheduler;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class LightConfigActivity extends ProtectedActivity {

    private DatabaseHelper db;
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
        setTitle(R.string.light_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.listItems);
        tvInfo = findViewById(R.id.tvListInfo);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                showActions(courts.get(position)));
        findViewById(R.id.fabAdd).setVisibility(android.view.View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LightScheduler.rescheduleAll(this);
        reload();
    }

    private void reload() {
        courts.clear();
        courts.addAll(db.getAllCourts());
        List<String> rows = new ArrayList<>();
        for (Court court : courts) {
            LightConfig config = db.getLightConfig(court.getId());
            String status = config == null ? getString(R.string.light_not_configured)
                    : config.type + " • " + config.status;
            rows.add(court.getName() + "\n" + status);
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(getString(R.string.result_count, courts.size()));
    }

    private void showActions(Court court) {
        LightConfig config = db.getLightConfig(court.getId());
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.edit_court));
        if (config != null && config.isConfigured()) {
            actions.add(getString(R.string.light_test_on));
            actions.add(getString(R.string.light_test_off));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(court.getName())
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        showForm(court, config);
                    } else if (which == 1) {
                        LightController.test(this, config, true, this::showResult);
                    } else if (which == 2) {
                        LightController.test(this, config, false, this::showResult);
                    }
                })
                .show();
    }

    private void showResult(boolean success, String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        reload();
    }

    private void showForm(Court court, LightConfig existing) {
        LightConfig config = existing == null ? new LightConfig() : existing;
        config.courtId = court.getId();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{getString(R.string.light_esp32),
                        getString(R.string.light_home_assistant)}));
        type.setSelection(LightConfig.TYPE_HOME_ASSISTANT.equals(config.type) ? 1 : 0);
        EditText onUrl = input(getString(R.string.light_on_url), config.onUrl);
        EditText offUrl = input(getString(R.string.light_off_url), config.offUrl);
        EditText entity = input(getString(R.string.light_entity), config.entityId);
        EditText token = input(getString(R.string.light_token), config.token);
        Spinner method = new Spinner(this);
        method.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"GET", "POST"}));
        method.setSelection("POST".equalsIgnoreCase(config.method) ? 1 : 0);
        EditText timeout = numberInput(getString(R.string.light_timeout),
                config.timeoutMs <= 0 ? 5000 : config.timeoutMs);
        CheckBox autoOff = new CheckBox(this);
        autoOff.setText(R.string.light_auto_off);
        autoOff.setChecked(config.autoOff);
        EditText grace = numberInput(getString(R.string.light_grace_minutes),
                Math.max(0, config.graceMinutes));
        TextView autoHint = new TextView(this);
        autoHint.setText(R.string.light_auto_off_hint);
        autoHint.setTextSize(12);
        layout.addView(type);
        layout.addView(onUrl);
        layout.addView(offUrl);
        layout.addView(entity);
        layout.addView(token);
        layout.addView(method);
        layout.addView(timeout);
        layout.addView(autoOff);
        layout.addView(grace);
        layout.addView(autoHint);

        new MaterialAlertDialogBuilder(this)
                .setTitle(court.getName())
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    config.type = type.getSelectedItemPosition() == 1
                            ? LightConfig.TYPE_HOME_ASSISTANT : LightConfig.TYPE_ESP32;
                    config.onUrl = onUrl.getText().toString().trim();
                    config.offUrl = offUrl.getText().toString().trim();
                    config.entityId = entity.getText().toString().trim();
                    config.token = token.getText().toString().trim();
                    config.method = method.getSelectedItem().toString();
                    config.timeoutMs = parseInt(timeout, 5000);
                    config.autoOff = autoOff.isChecked();
                    config.graceMinutes = Math.max(0, parseInt(grace, 0));
                    config.status = LightConfig.STATUS_UNKNOWN;
                    config.lastError = null;
                    db.saveLightConfig(config);
                    LightScheduler.rescheduleAll(this);
                    Toast.makeText(this, R.string.light_saved, Toast.LENGTH_SHORT).show();
                    promptExactAlarm(config);
                    reload();
                })
                .setNeutralButton(R.string.light_test_on, (dialog, which) -> {
                    config.type = type.getSelectedItemPosition() == 1
                            ? LightConfig.TYPE_HOME_ASSISTANT : LightConfig.TYPE_ESP32;
                    config.onUrl = onUrl.getText().toString().trim();
                    config.offUrl = offUrl.getText().toString().trim();
                    config.entityId = entity.getText().toString().trim();
                    config.token = token.getText().toString().trim();
                    config.method = method.getSelectedItem().toString();
                    config.timeoutMs = parseInt(timeout, 5000);
                    config.autoOff = autoOff.isChecked();
                    config.graceMinutes = Math.max(0, parseInt(grace, 0));
                    LightController.test(this, config, true, this::showResult);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptExactAlarm(LightConfig config) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || !config.autoOff || !config.isConfigured()) {
            return;
        }
        AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (manager == null || manager.canScheduleExactAlarms()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.light_exact_alarm_title)
                .setMessage(R.string.light_exact_alarm_message)
                .setPositiveButton(R.string.light_open_settings,
                        (dialog, which) -> openExactAlarmSettings())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openExactAlarmSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
            } catch (Exception ignored) {
            }
        }
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
