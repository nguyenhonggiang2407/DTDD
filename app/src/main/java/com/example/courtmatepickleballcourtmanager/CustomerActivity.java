package com.example.courtmatepickleballcourtmanager;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.courtmatepickleballcourtmanager.adapter.CustomerAdapter;
import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.Customer;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class CustomerActivity extends ProtectedActivity
        implements CustomerAdapter.OnCustomerClickListener {

    private DatabaseHelper db;
    private CustomerAdapter adapter;
    private final List<Customer> customers = new ArrayList<>();
    private TextInputEditText etSearchCustomer;
    private TextView tvCustomerResultCount;
    private TextView tvEmptyCustomersTitle;
    private TextView tvEmptyCustomersHint;
    private View layoutEmpty;
    private SwipeRefreshLayout swipeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_customer);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.title_customers);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        layoutEmpty = findViewById(R.id.layoutEmptyCustomers);
        tvCustomerResultCount = findViewById(R.id.tvCustomerResultCount);
        tvEmptyCustomersTitle = findViewById(R.id.tvEmptyCustomersTitle);
        tvEmptyCustomersHint = findViewById(R.id.tvEmptyCustomersHint);
        etSearchCustomer = findViewById(R.id.etSearchCustomer);
        swipeRefresh = findViewById(R.id.swipeRefreshCustomer);
        RecyclerView recyclerView = findViewById(R.id.rvCustomers);
        adapter = new CustomerAdapter(customers, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                reload();
                swipeRefresh.setRefreshing(false);
            });
        }

        etSearchCustomer.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                reload();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        etSearchCustomer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(v);
                return true;
            }
            return false;
        });

        findViewById(R.id.fabAddCustomer).setOnClickListener(v -> showCustomerDialog(null));
        findViewById(R.id.btnArchivedCustomers).setOnClickListener(v ->
                showArchivedCustomers());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reload() {
        String keyword = etSearchCustomer.getText() == null
                ? "" : etSearchCustomer.getText().toString();
        List<Customer> result = db.searchCustomers(keyword);
        customers.clear();
        customers.addAll(result);
        adapter.replaceAll(result);
        layoutEmpty.setVisibility(customers.isEmpty() ? View.VISIBLE : View.GONE);
        tvCustomerResultCount.setText(getString(R.string.result_count_customers,
                customers.size()));
        boolean searching = !keyword.trim().isEmpty();
        tvEmptyCustomersTitle.setText(searching
                ? R.string.empty_search_result : R.string.empty_customers);
        tvEmptyCustomersHint.setText(searching
                ? R.string.empty_search_hint : R.string.empty_customers_hint);
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
    public void onCustomerClick(Customer customer) {
        String[] actions = {
                getString(R.string.edit_customer),
                getString(R.string.delete_customer),
                getString(R.string.call_customer),
                getString(R.string.copy_phone),
                getString(R.string.close)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(customer.getName())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showCustomerDialog(customer);
                    } else if (which == 1) {
                        confirmDelete(customer);
                    } else if (which == 2) {
                        try {
                            android.content.Intent callIntent = new android.content.Intent(android.content.Intent.ACTION_DIAL);
                            callIntent.setData(android.net.Uri.parse("tel:" + customer.getPhone()));
                            startActivity(callIntent);
                        } catch (Exception e) {
                            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 3) {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("phone", customer.getPhone());
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                        }
                        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showCustomerDialog(@Nullable Customer existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_customer, null);
        TextInputEditText etName = view.findViewById(R.id.etCustomerName);
        TextInputEditText etPhone = view.findViewById(R.id.etCustomerPhone);

        if (existing != null) {
            etName.setText(existing.getName());
            etPhone.setText(existing.getPhone());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add_customer : R.string.edit_customer)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = etName.getText() == null
                            ? "" : etName.getText().toString().trim();
                    String phone = etPhone.getText() == null
                            ? "" : etPhone.getText().toString().replaceAll("[^0-9]", "");

                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.error_customer_name,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (phone.length() < 9 || phone.length() > 11) {
                        Toast.makeText(this, R.string.error_customer_phone,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Customer duplicate = db.findCustomerByPhone(phone);
                    if (duplicate != null && (existing == null
                            || duplicate.getId() != existing.getId())) {
                        Toast.makeText(this, R.string.error_customer_phone_exists,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Customer customer = existing == null ? new Customer() : existing;
                    customer.setName(name);
                    customer.setPhone(phone);

                    long result = existing == null
                            ? db.insertCustomer(customer) : db.updateCustomer(customer);
                    if (result <= 0) {
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, R.string.toast_saved_customer, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    reload();
                }));

        dialog.show();
    }

    private void confirmDelete(Customer customer) {
        int count = db.countBookingsForCustomer(customer.getId());
        String message = count > 0
                ? getString(R.string.archive_customer_with_bookings, count)
                : getString(R.string.confirm_archive_customer_message, customer.getName());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_delete_customer_title)
                .setMessage(message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    int rows = db.deleteCustomer(customer.getId());
                    if (rows > 0) {
                        Toast.makeText(this, R.string.customer_archived, Toast.LENGTH_SHORT).show();
                        reload();
                    } else {
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showArchivedCustomers() {
        List<Customer> inactive = db.getInactiveCustomers("");
        if (inactive.isEmpty()) {
            Toast.makeText(this, R.string.archived_customers_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[inactive.size()];
        for (int i = 0; i < inactive.size(); i++) {
            Customer customer = inactive.get(i);
            names[i] = customer.getName() + " • " + customer.getPhone();
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.archived_customers)
                .setItems(names, (dialog, which) -> confirmRestore(inactive.get(which)))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void confirmRestore(Customer customer) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restore_customer)
                .setMessage(getString(R.string.restore_customer_message, customer.getName()))
                .setPositiveButton(R.string.restore_confirm, (dialog, which) -> {
                    int rows = db.restoreCustomer(customer.getId());
                    if (rows > 0) {
                        Toast.makeText(this, R.string.restore_customer_success,
                                Toast.LENGTH_SHORT).show();
                        reload();
                    } else {
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
