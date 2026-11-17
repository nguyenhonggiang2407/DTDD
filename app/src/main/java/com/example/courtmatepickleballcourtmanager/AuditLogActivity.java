package com.example.courtmatepickleballcourtmanager;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.ActivityLog;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

public class AuditLogActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private ArrayAdapter<String> adapter;
    private TextView tvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.audit_log_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.fabAdd).setVisibility(android.view.View.GONE);
        tvInfo = findViewById(R.id.tvListInfo);
        ListView listView = findViewById(R.id.listItems);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                android.R.id.text1, new ArrayList<>());
        listView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        List<ActivityLog> logs = db.getRecentActivities(200);
        List<String> rows = new ArrayList<>();
        for (ActivityLog log : logs) {
            StringBuilder row = new StringBuilder();
            row.append(DateTimeUtils.displayDateTime(log.time))
                    .append(" • ").append(actionLabel(log.action));
            row.append("\n").append(targetLabel(log.target));
            if (log.targetId > 0) {
                row.append(" #").append(log.targetId);
            }
            if (log.result != null && !log.result.isEmpty()) {
                row.append(" • ").append(log.result);
            }
            String change = describeChange(log.oldValue, log.newValue);
            if (!change.isEmpty()) {
                row.append("\n").append(change);
            }
            rows.add(row.toString());
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(logs.isEmpty()
                ? getString(R.string.audit_log_empty)
                : getString(R.string.audit_log_count, logs.size()));
    }

    private String describeChange(String oldValue, String newValue) {
        boolean hasOld = oldValue != null && !oldValue.isEmpty();
        boolean hasNew = newValue != null && !newValue.isEmpty();
        if (!hasOld && !hasNew) {
            return "";
        }
        if (hasOld && hasNew) {
            return oldValue + " → " + newValue;
        }
        return hasOld ? oldValue : newValue;
    }

    private String actionLabel(String action) {
        if (action == null) {
            return "";
        }
        switch (action) {
            case "THANH_TOAN":
                return getString(R.string.audit_action_payment);
            case "HUY_LICH":
                return getString(R.string.audit_action_cancel_booking);
            case "XOA_LICH":
                return getString(R.string.audit_action_delete_booking);
            case "NGUNG_KHACH":
                return getString(R.string.audit_action_deactivate_customer);
            case "TRA_THIET_BI":
                return getString(R.string.audit_action_return_rental);
            case "XOA_SAN":
                return getString(R.string.audit_action_delete_court);
            case "XOA_DICH_VU":
                return getString(R.string.audit_action_delete_service);
            default:
                return action;
        }
    }

    private String targetLabel(String target) {
        if (target == null) {
            return "";
        }
        switch (target) {
            case "phien_choi":
                return getString(R.string.target_session);
            case "dat_lich":
                return getString(R.string.target_booking);
            case "phieu_thue":
                return getString(R.string.target_rental);
            case "khach_hang":
                return getString(R.string.target_customer);
            case "san":
                return getString(R.string.target_court);
            case "dich_vu":
                return getString(R.string.target_service);
            default:
                return target;
        }
    }
}
