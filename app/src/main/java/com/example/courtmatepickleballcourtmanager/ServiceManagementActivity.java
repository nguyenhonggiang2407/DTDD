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
import com.example.courtmatepickleballcourtmanager.model.Service;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ServiceManagementActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private final List<Service> services = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView tvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_list);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.service_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.listItems);
        tvInfo = findViewById(R.id.tvListInfo);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                showForm(services.get(position)));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            showActions(services.get(position));
            return true;
        });
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
        services.clear();
        services.addAll(db.getAllServices());
        List<String> rows = new ArrayList<>();
        for (Service service : services) {
            String type = service.isRental() ? getString(R.string.service_rental)
                    : getString(R.string.service_sale);
            String status = service.isActive() ? getString(R.string.service_status_active)
                    : getString(R.string.service_status_paused);
            rows.add(service.name + " • " + MoneyUtils.format(service.price) + "/"
                    + service.unit + "\n" + type + " • " + status + " • Tồn: " + service.stock
                    + (service.isRental() ? " • Cọc: " + MoneyUtils.format(service.deposit) : ""));
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(getString(R.string.result_count, services.size()));
    }

    private void showForm(Service existing) {
        Service service = existing == null ? new Service() : existing;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        EditText name = input(getString(R.string.service_name), InputType.TYPE_CLASS_TEXT);
        Spinner type = new Spinner(this);
        type.setAdapter(spinnerAdapter(new String[]{
                getString(R.string.service_sale), getString(R.string.service_rental)}));
        type.setSelection(service.isRental() ? 1 : 0);
        EditText unit = input(getString(R.string.service_unit), InputType.TYPE_CLASS_TEXT);
        EditText price = numberInput(getString(R.string.service_price), service.price);
        EditText deposit = numberInput(getString(R.string.service_deposit), service.deposit);
        EditText late = numberInput(getString(R.string.service_late_fee), service.lateFee);
        EditText stock = numberInput(getString(R.string.service_stock), service.stock);
        name.setText(service.name == null ? "" : service.name);
        unit.setText(service.unit == null ? "" : service.unit);
        layout.addView(name);
        layout.addView(label(R.string.service_type));
        layout.addView(type);
        layout.addView(unit);
        layout.addView(price);
        layout.addView(deposit);
        layout.addView(late);
        layout.addView(stock);

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add : R.string.service_title)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    service.name = name.getText().toString().trim();
                    service.type = type.getSelectedItemPosition() == 1
                            ? Service.TYPE_RENTAL : Service.TYPE_SALE;
                    service.unit = unit.getText().toString().trim();
                    service.price = parseInt(price, 0);
                    service.deposit = parseInt(deposit, 0);
                    service.lateFee = parseInt(late, 0);
                    service.stock = parseInt(stock, 0);
                    if (service.name.isEmpty()) {
                        return;
                    }
                    if (existing == null) {
                        db.insertService(service);
                    } else {
                        db.updateService(service);
                    }
                    Toast.makeText(this, R.string.service_saved, Toast.LENGTH_SHORT).show();
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showActions(Service service) {
        String[] actions = {
                getString(R.string.edit),
                getString(service.isActive()
                        ? R.string.service_pause : R.string.service_resume),
                getString(R.string.delete),
                getString(R.string.close)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(service.name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showForm(service);
                    } else if (which == 1) {
                        toggleStatus(service);
                    } else if (which == 2) {
                        confirmDelete(service);
                    }
                })
                .show();
    }

    private void toggleStatus(Service service) {
        service.status = service.isActive()
                ? Service.STATUS_PAUSED : Service.STATUS_ACTIVE;
        int rows = db.updateService(service);
        if (rows > 0) {
            Toast.makeText(this, service.isActive()
                    ? R.string.service_resumed : R.string.service_paused, Toast.LENGTH_SHORT).show();
            reload();
        } else {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(Service service) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.service_delete_confirm, service.name))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    int rows = db.deleteService(service.id);
                    if (rows > 0) {
                        Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
                        reload();
                    } else if (rows == DatabaseHelper.ERROR_REFERENCED) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.service_delete_blocked_title)
                                .setMessage(R.string.service_delete_blocked_message)
                                .setPositiveButton(R.string.service_pause,
                                        (d, w) -> toggleStatus(service))
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    } else {
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private EditText input(String hint, int type) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(type);
        return editText;
    }

    private EditText numberInput(String hint, int value) {
        EditText editText = input(hint, InputType.TYPE_CLASS_NUMBER);
        editText.setText(String.valueOf(value));
        return editText;
    }

    private TextView label(int text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setPadding(0, dp(10), 0, 0);
        return view;
    }

    private ArrayAdapter<String> spinnerAdapter(String[] values) {
        return new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
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
