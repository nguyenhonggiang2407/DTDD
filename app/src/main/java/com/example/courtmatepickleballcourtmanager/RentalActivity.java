package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
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
import com.example.courtmatepickleballcourtmanager.model.Rental;
import com.example.courtmatepickleballcourtmanager.model.RentalItem;
import com.example.courtmatepickleballcourtmanager.model.Service;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.example.courtmatepickleballcourtmanager.util.PhoneUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RentalActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private final List<Rental> rentals = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView tvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_list);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.rental_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.listItems);
        tvInfo = findViewById(R.id.tvListInfo);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                showRental(rentals.get(position)));
        ExtendedFloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setText(R.string.add);
        fab.setOnClickListener(v -> createRental());
    }

    @Override
    protected void onResume() {
        super.onResume();
        db.refreshOverdueRentals(DateTimeUtils.nowDbDateTime());
        reload();
    }

    private void reload() {
        rentals.clear();
        rentals.addAll(db.getRentals(null));
        List<String> rows = new ArrayList<>();
        for (Rental rental : rentals) {
            String status = statusLabel(rental.status);
            rows.add(rental.customerName + " • " + status + "\n"
                    + "Hạn trả: " + rental.dueTime + " • Cọc: "
                    + MoneyUtils.format(rental.deposit));
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(getString(R.string.result_count, rentals.size()));
    }

    private void createRental() {
        List<Service> services = db.getServicesByType(Service.TYPE_RENTAL);
        if (services.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.rental_no_services_title)
                    .setMessage(R.string.rental_no_services_message)
                    .setPositiveButton(R.string.rental_add_service, (dialog, which) ->
                            startActivity(new Intent(this, ServiceManagementActivity.class)))
                    .setNegativeButton(R.string.close, null)
                    .show();
            return;
        }
        List<String> labels = new ArrayList<>();
        for (Service service : services) {
            labels.add(service.name + " • " + MoneyUtils.format(service.price)
                    + " • cọc " + MoneyUtils.format(service.deposit)
                    + " • tồn " + service.stock);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rent_equipment)
                .setItems(labels.toArray(new String[0]), (dialog, which) ->
                        showCreateForm(services.get(which)))
                .show();
    }

    private void showCreateForm(Service service) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        EditText name = input(getString(R.string.customer_name_hint), "");
        EditText phone = input(getString(R.string.phone_hint), "");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText quantity = numberInput(getString(R.string.rental_quantity), 1);
        EditText due = input(getString(R.string.rental_due),
                DateTimeUtils.addHoursToDbDateTime(DateTimeUtils.nowDbDateTime(), 4));
        layout.addView(name);
        layout.addView(phone);
        layout.addView(quantity);
        layout.addView(due);

        new MaterialAlertDialogBuilder(this)
                .setTitle(service.name)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String customerName = name.getText().toString().trim();
                    String customerPhone = PhoneUtils.normalize(phone.getText().toString());
                    if (customerName.isEmpty()) {
                        Toast.makeText(this, R.string.error_customer_name,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!PhoneUtils.isValid(customerPhone)) {
                        Toast.makeText(this, R.string.error_customer_phone,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int quantityValue = parseInt(quantity, 0);
                    if (quantityValue <= 0) {
                        Toast.makeText(this, R.string.rental_quantity_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String dueValue = due.getText().toString().trim();
                    if (DateTimeUtils.parseDbDateTime(dueValue) == null) {
                        Toast.makeText(this, R.string.rental_due_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int customerId = db.findOrCreateCustomer(customerName, customerPhone);
                    if (customerId < 0) {
                        Toast.makeText(this, R.string.error_customer_phone,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    RentalItem item = new RentalItem();
                    item.serviceId = service.id;
                    item.quantity = quantityValue;
                    List<RentalItem> items = new ArrayList<>();
                    items.add(item);
                    long id = db.createRental(customerId, 0,
                            DateTimeUtils.nowDbDateTime(), dueValue, items);
                    if (id > 0) {
                        Toast.makeText(this, R.string.rental_created, Toast.LENGTH_SHORT).show();
                        reload();
                    } else {
                        Toast.makeText(this, R.string.service_order_error,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showRental(Rental rental) {
        StringBuilder info = new StringBuilder();
        info.append(rental.customerName).append(" • ").append(rental.customerPhone)
                .append("\nHạn trả: ").append(rental.dueTime)
                .append("\nCọc: ").append(MoneyUtils.format(rental.deposit))
                .append(" • Phí thuê: ").append(MoneyUtils.format(rental.rentFee))
                .append("\nTrạng thái: ").append(statusLabel(rental.status));
        for (RentalItem item : db.getRentalItems(rental.id)) {
            info.append("\n• ").append(item.serviceName).append(" × ")
                    .append(item.quantity).append(" = ")
                    .append(MoneyUtils.format(item.amount));
        }
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.view_detail));
        if (!Rental.STATUS_RETURNED.equals(rental.status)) {
            actions.add(getString(R.string.return_item));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rental_items)
                .setMessage(info.toString())
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    if (which == 1) {
                        showReturnForm(rental);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void showReturnForm(Rental rental) {
        String now = DateTimeUtils.nowDbDateTime();
        int minutes = com.example.courtmatepickleballcourtmanager.util.RentalCalculator
                .lateMinutes(rental.dueTime, now);
        int late = com.example.courtmatepickleballcourtmanager.util.RentalCalculator
                .lateFeeForOutstandingItems(db.getRentalItems(rental.id), minutes);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        EditText lateFee = numberInput(getString(R.string.rental_late_fee), late);
        EditText compensation = numberInput(getString(R.string.rental_compensation), 0);

        List<RentalItem> pending = new ArrayList<>();
        List<EditText> quantityInputs = new ArrayList<>();
        for (RentalItem item : db.getRentalItems(rental.id)) {
            int remaining = Math.max(0, item.quantity - item.returnedQuantity);
            if (remaining <= 0) {
                continue;
            }
            pending.add(item);
            EditText quantity = numberInput(item.serviceName + " (còn " + remaining + ")",
                    remaining);
            quantityInputs.add(quantity);
            layout.addView(quantity);
        }
        layout.addView(lateFee);
        layout.addView(compensation);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rental_return_title)
                .setView(layout)
                .setPositiveButton(R.string.return_item, (dialog, which) -> {
                    int lateValue = parseInt(lateFee, 0);
                    int compensationValue = parseInt(compensation, 0);
                    Map<Integer, Integer> returned = new HashMap<>();
                    for (int i = 0; i < pending.size(); i++) {
                        returned.put(pending.get(i).id, parseInt(quantityInputs.get(i), 0));
                    }
                    int rows = db.returnRental(rental.id, now, lateValue,
                            compensationValue, null, returned);
                    if (rows > 0) {
                        int remainingAfter = 0;
                        for (RentalItem item : db.getRentalItems(rental.id)) {
                            remainingAfter += Math.max(0,
                                    item.quantity - item.returnedQuantity);
                        }
                        if (remainingAfter == 0) {
                            Rental updated = db.getRental(rental.id);
                            int refund = updated == null ? 0 : updated.refund;
                            Toast.makeText(this, getString(R.string.rental_returned,
                                    MoneyUtils.format(refund)), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, R.string.rental_partial_returned,
                                    Toast.LENGTH_SHORT).show();
                        }
                        db.refreshOverdueRentals(DateTimeUtils.nowDbDateTime());
                        reload();
                    } else {
                        Toast.makeText(this, R.string.rental_quantity_invalid,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String statusLabel(String status) {
        switch (status) {
            case Rental.STATUS_OVERDUE:
                return "Quá hạn";
            case Rental.STATUS_RETURNED:
                return "Đã trả";
            default:
                return "Đang thuê";
        }
    }

    private EditText input(String hint, String value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value);
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
