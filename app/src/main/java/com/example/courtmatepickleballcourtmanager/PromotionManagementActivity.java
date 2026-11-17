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
import com.example.courtmatepickleballcourtmanager.model.Promotion;
import com.example.courtmatepickleballcourtmanager.model.SpecialDate;
import com.example.courtmatepickleballcourtmanager.util.PromotionEngine;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class PromotionManagementActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private final List<Promotion> promotions = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView tvInfo;

    private static final String[] SCOPE_VALUES = {
            Promotion.SCOPE_COURT, Promotion.SCOPE_SERVICE, Promotion.SCOPE_CLASS,
            Promotion.SCOPE_EVENT, Promotion.SCOPE_ALL
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_list);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.promotion_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.listItems);
        tvInfo = findViewById(R.id.tvListInfo);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                showActions(promotions.get(position)));
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
        promotions.clear();
        promotions.addAll(db.getPromotions());
        List<String> rows = new ArrayList<>();
        for (Promotion promotion : promotions) {
            rows.add(promotion.name + " • " + PromotionEngine.discountLabel(promotion) + "\n"
                    + PromotionEngine.scopeLabel(promotion.scope)
                    + (promotion.code == null ? "" : " • Mã: " + promotion.code)
                    + " • " + promotion.status);
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(getString(R.string.result_count, promotions.size()));
    }

    private void showForm(Promotion existing) {
        Promotion promotion = existing == null ? new Promotion() : existing;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        EditText name = input(getString(R.string.promotion_name), promotion.name);
        Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{getString(R.string.promotion_amount),
                        getString(R.string.promotion_percent)}));
        type.setSelection(promotion.isPercent() ? 1 : 0);
        EditText value = numberInput(getString(R.string.promotion_value), promotion.value);
        Spinner scope = new Spinner(this);
        List<String> scopes = new ArrayList<>();
        int scopeSelection = 4;
        for (int i = 0; i < SCOPE_VALUES.length; i++) {
            scopes.add(PromotionEngine.scopeLabel(SCOPE_VALUES[i]));
            if (SCOPE_VALUES[i].equals(promotion.scope)) {
                scopeSelection = i;
            }
        }
        scope.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, scopes));
        scope.setSelection(scopeSelection);
        EditText code = input(getString(R.string.promotion_code), promotion.code);
        EditText from = input(getString(R.string.promotion_from), promotion.fromDate);
        EditText to = input(getString(R.string.promotion_to), promotion.toDate);
        EditText min = numberInput(getString(R.string.promotion_min), promotion.minOrder);
        EditText max = numberInput(getString(R.string.promotion_max), promotion.maxDiscount);
        CheckBox annual = new CheckBox(this);
        annual.setText(R.string.promotion_annual);
        annual.setChecked(promotion.annual == 1);
        layout.addView(name);
        layout.addView(type);
        layout.addView(value);
        layout.addView(scope);
        layout.addView(code);
        layout.addView(from);
        layout.addView(to);
        layout.addView(min);
        layout.addView(max);
        layout.addView(annual);

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add : R.string.promotion_title)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    promotion.name = name.getText().toString().trim();
                    promotion.discountType = type.getSelectedItemPosition() == 1
                            ? Promotion.TYPE_PERCENT : Promotion.TYPE_AMOUNT;
                    promotion.value = parseInt(value, 0);
                    promotion.scope = SCOPE_VALUES[scope.getSelectedItemPosition()];
                    promotion.code = code.getText().toString().trim();
                    promotion.fromDate = from.getText().toString().trim();
                    promotion.toDate = to.getText().toString().trim();
                    promotion.minOrder = parseInt(min, 0);
                    promotion.maxDiscount = parseInt(max, 0);
                    promotion.annual = annual.isChecked() ? 1 : 0;
                    if (promotion.name.isEmpty() || promotion.value <= 0) {
                        return;
                    }
                    if (existing == null) {
                        promotion.status = Promotion.STATUS_ACTIVE;
                        db.insertPromotion(promotion);
                    } else {
                        db.updatePromotion(promotion);
                    }
                    Toast.makeText(this, R.string.promotion_saved, Toast.LENGTH_SHORT).show();
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showActions(Promotion promotion) {
        String[] actions = {
                getString(R.string.edit_court),
                getString(R.string.promotion_link_dates),
                getString(R.string.delete_item),
                getString(R.string.close)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(promotion.name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showForm(promotion);
                    } else if (which == 1) {
                        linkDates(promotion);
                    } else if (which == 2) {
                        confirmDelete(promotion);
                    }
                })
                .show();
    }

    private void linkDates(Promotion promotion) {
        List<SpecialDate> dates = db.getSpecialDates();
        if (dates.isEmpty()) {
            Toast.makeText(this, R.string.stats_no_data, Toast.LENGTH_SHORT).show();
            return;
        }
        List<Integer> linked = db.getPromotionSpecialDateIds(promotion.id);
        String[] labels = new String[dates.size()];
        boolean[] checked = new boolean[dates.size()];
        for (int i = 0; i < dates.size(); i++) {
            SpecialDate date = dates.get(i);
            labels[i] = date.name + " (" + date.day + "/" + date.month + ")";
            for (Integer id : linked) {
                if (id == date.id) {
                    checked[i] = true;
                    break;
                }
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.promotion_link_dates)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) ->
                        checked[which] = isChecked)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    List<Integer> ids = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            ids.add(dates.get(i).id);
                        }
                    }
                    db.savePromotionSpecialDates(promotion.id, ids);
                    Toast.makeText(this, R.string.promotion_dates_saved,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(Promotion promotion) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.promotion_delete_confirm, promotion.name))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    db.deletePromotion(promotion.id);
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
