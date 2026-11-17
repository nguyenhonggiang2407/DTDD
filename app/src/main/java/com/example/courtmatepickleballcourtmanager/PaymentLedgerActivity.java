package com.example.courtmatepickleballcourtmanager;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.CourtSession;
import com.example.courtmatepickleballcourtmanager.model.Payment;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

public class PaymentLedgerActivity extends ProtectedActivity {

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
        setTitle(R.string.payment_ledger_title);
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
        List<Payment> payments = db.getRecentPayments(200);
        List<String> rows = new ArrayList<>();
        int total = 0;
        for (Payment payment : payments) {
            boolean refund = Payment.TYPE_REFUND.equals(payment.transactionType);
            boolean adjustment = Payment.TYPE_ADJUST.equals(payment.transactionType);
            // Điều chỉnh cọc chỉ là cấn trừ nội bộ, không phải dòng tiền ra.
            total += refund ? -payment.amount : (adjustment ? 0 : payment.amount);
            StringBuilder row = new StringBuilder();
            row.append(DateTimeUtils.displayDateTime(payment.time))
                    .append(" • ").append(transactionLabel(payment.transactionType));
            row.append("\n").append(refund ? "− " : "")
                    .append(MoneyUtils.format(payment.amount))
                    .append(" • ").append(methodLabel(payment.method))
                    .append(" • ").append(targetLabel(payment.targetType))
                    .append(" #").append(payment.targetId);
            if (payment.note != null && !payment.note.isEmpty()) {
                row.append("\n").append(payment.note);
            }
            rows.add(row.toString());
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(payments.isEmpty()
                ? getString(R.string.payment_ledger_empty)
                : getString(R.string.payment_ledger_summary, payments.size(),
                MoneyUtils.format(total)));
    }

    private String transactionLabel(String type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case Payment.TYPE_DEPOSIT:
                return getString(R.string.transaction_deposit);
            case Payment.TYPE_REFUND:
                return getString(R.string.transaction_refund);
            case Payment.TYPE_ADJUST:
                return getString(R.string.transaction_adjust);
            default:
                return getString(R.string.transaction_payment);
        }
    }

    private String methodLabel(String method) {
        if (CourtSession.METHOD_MOMO.equals(method)) {
            return getString(R.string.payment_method_momo_legacy);
        }
        if (CourtSession.METHOD_QR.equals(method)) {
            return getString(R.string.payment_qr);
        }
        return getString(R.string.payment_cash);
    }

    private String targetLabel(String target) {
        if (target == null) {
            return "";
        }
        switch (target) {
            case Payment.TARGET_SESSION:
                return getString(R.string.target_session);
            case Payment.TARGET_BOOKING:
                return getString(R.string.target_booking);
            case Payment.TARGET_RENTAL:
                return getString(R.string.target_rental);
            case Payment.TARGET_CLASS:
                return getString(R.string.target_class);
            case Payment.TARGET_EVENT:
                return getString(R.string.target_event);
            default:
                return getString(R.string.target_other);
        }
    }
}
