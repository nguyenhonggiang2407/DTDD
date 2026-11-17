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

import com.example.courtmatepickleballcourtmanager.adapter.CourtAdapter;
import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CourtActivity extends ProtectedActivity implements CourtAdapter.OnCourtClickListener {

    private DatabaseHelper db;
    private CourtAdapter adapter;
    private final List<Court> courts = new ArrayList<>();
    private TextInputEditText etSearchCourt;
    private TextView tvCourtResultCount;
    private TextView tvEmptyCourtsTitle;
    private TextView tvEmptyCourtsHint;
    private View layoutEmpty;
    private SwipeRefreshLayout swipeRefresh;
    private String filterStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_court);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.title_courts);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        layoutEmpty = findViewById(R.id.layoutEmptyCourts);
        etSearchCourt = findViewById(R.id.etSearchCourt);
        tvCourtResultCount = findViewById(R.id.tvCourtResultCount);
        tvEmptyCourtsTitle = findViewById(R.id.tvEmptyCourtsTitle);
        tvEmptyCourtsHint = findViewById(R.id.tvEmptyCourtsHint);
        swipeRefresh = findViewById(R.id.swipeRefreshCourt);
        RecyclerView recyclerView = findViewById(R.id.rvCourts);
        adapter = new CourtAdapter(courts, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                reload();
                swipeRefresh.setRefreshing(false);
            });
        }

        etSearchCourt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                reload();
            }
        });
        etSearchCourt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(v);
                return true;
            }
            return false;
        });

        ChipGroup chipGroupStatus = findViewById(R.id.chipGroupCourtStatus);
        chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checkedId = checkedIds.isEmpty()
                    ? R.id.chipAllCourts : checkedIds.get(0);
            if (checkedId == R.id.chipActiveCourts) {
                filterStatus = Court.STATUS_ACTIVE;
            } else if (checkedId == R.id.chipMaintenanceCourts) {
                filterStatus = Court.STATUS_MAINTENANCE;
            } else {
                filterStatus = null;
            }
            reload();
        });

        findViewById(R.id.fabAddCourt).setOnClickListener(v -> showCourtDialog(null));
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
        String keyword = etSearchCourt.getText() == null
                ? "" : etSearchCourt.getText().toString().trim();
        String needle = keyword.toLowerCase(Locale.getDefault());
        List<Court> result = new ArrayList<>();
        for (Court court : db.getAllCourts()) {
            String name = court.getName() == null ? "" : court.getName();
            boolean matchesName = keyword.isEmpty() || name.toLowerCase(Locale.getDefault())
                    .contains(needle);
            boolean matchesStatus = filterStatus == null
                    || filterStatus.equals(court.getStatus());
            if (matchesName && matchesStatus) {
                result.add(court);
            }
        }

        courts.clear();
        courts.addAll(result);
        adapter.replaceAll(result);
        boolean empty = courts.isEmpty();
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        tvCourtResultCount.setText(getString(R.string.result_count_courts, courts.size()));

        boolean searching = !keyword.isEmpty() || filterStatus != null;
        tvEmptyCourtsTitle.setText(searching
                ? R.string.empty_search_result : R.string.empty_courts);
        tvEmptyCourtsHint.setText(searching
                ? R.string.empty_search_hint : R.string.empty_courts_hint);
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
    public void onCourtClick(Court court) {
        String[] actions = {
                getString(R.string.title_court_sessions),
                getString(R.string.edit_court),
                getString(R.string.delete_court),
                getString(R.string.close)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(court.getName())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new android.content.Intent(this, CourtSessionActivity.class));
                    } else if (which == 1) {
                        showCourtDialog(court);
                    } else if (which == 2) {
                        confirmDelete(court);
                    }
                })
                .show();
    }

    private void showCourtDialog(@Nullable Court existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_court, null);
        TextInputEditText etName = view.findViewById(R.id.etCourtName);
        TextInputEditText etPrice = view.findViewById(R.id.etCourtPrice);
        MaterialSwitch swMaintenance = view.findViewById(R.id.swMaintenance);

        if (existing != null) {
            etName.setText(existing.getName());
            etPrice.setText(String.valueOf(existing.getPricePerHour()));
            swMaintenance.setChecked(!existing.isActive());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add_court : R.string.edit_court)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = etName.getText() == null
                            ? "" : etName.getText().toString().trim();
                    String priceText = etPrice.getText() == null
                            ? "" : etPrice.getText().toString().trim();
                    int price = MoneyUtils.parseOrDefault(priceText, 0);

                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.error_court_name, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (price <= 0) {
                        Toast.makeText(this, R.string.error_price, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Court court = existing == null ? new Court() : existing;
                    court.setName(name);
                    court.setPricePerHour(price);
                    court.setStatus(swMaintenance.isChecked()
                            ? Court.STATUS_MAINTENANCE : Court.STATUS_ACTIVE);

                    long result = existing == null
                            ? db.insertCourt(court) : db.updateCourt(court);
                    if (result <= 0) {
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, R.string.toast_saved_court, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    reload();
                }));

        dialog.show();
    }

    private void confirmDelete(Court court) {
        int count = db.countCourtReferences(court.getId());
        if (count > 0) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_court)
                    .setMessage(getString(R.string.cant_delete_court, count))
                    .setPositiveButton(R.string.close, null)
                    .show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_delete_court_title)
                .setMessage(getString(R.string.confirm_delete_court_message, court.getName()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    int rows = db.deleteCourt(court.getId());
                    if (rows > 0) {
                        Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
                        reload();
                    } else {
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
