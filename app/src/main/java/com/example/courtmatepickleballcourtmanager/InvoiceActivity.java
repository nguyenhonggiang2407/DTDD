package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.CourtSession;
import com.example.courtmatepickleballcourtmanager.util.InvoiceGenerator;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class InvoiceActivity extends ProtectedActivity {

    private DatabaseHelper db;
    private final List<CourtSession> sessions = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView tvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.invoice_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvInfo = findViewById(R.id.tvListInfo);
        ExtendedFloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setVisibility(View.GONE);
        ListView listView = findViewById(R.id.listItems);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                openSession(sessions.get(position)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        sessions.clear();
        sessions.addAll(db.getRecentFinishedSessions(200));
        List<String> rows = new ArrayList<>();
        for (CourtSession session : sessions) {
            String invoiceNo = session.invoiceNo == null || session.invoiceNo.isEmpty()
                    ? getString(R.string.invoice_not_issued) : session.invoiceNo;
            String customer = session.customerName == null
                    ? getString(R.string.session_walk_in_customer) : session.customerName;
            String status = CourtSession.PAYMENT_PAID.equals(session.paymentStatus)
                    ? getString(R.string.payment_paid) : getString(R.string.payment_unpaid);
            rows.add(invoiceNo + " • " + MoneyUtils.format(session.total)
                    + "\n" + (session.courtName == null ? "" : session.courtName)
                    + " • " + customer + " • " + status);
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        tvInfo.setText(getString(R.string.invoice_count, sessions.size()));
    }

    private void openSession(CourtSession session) {
        if (!CourtSession.PAYMENT_PAID.equals(session.paymentStatus)) {
            Intent intent = new Intent(this, PaymentActivity.class);
            intent.putExtra(PaymentActivity.EXTRA_SESSION_ID, session.id);
            startActivity(intent);
            return;
        }
        String[] actions = {
                getString(R.string.invoice_view_pdf),
                getString(R.string.invoice_share)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(session.invoiceNo == null
                        ? getString(R.string.invoice_title) : session.invoiceNo)
                .setItems(actions, (dialog, which) -> {
                    File file = invoicePdf(session);
                    if (file == null) {
                        return;
                    }
                    if (which == 0) {
                        InvoiceGenerator.open(this, file);
                    } else {
                        InvoiceGenerator.share(this, file);
                    }
                })
                .show();
    }

    private File invoicePdf(CourtSession session) {
        try {
            return InvoiceGenerator.createPdf(this, session,
                    db.getServiceOrders(session.id),
                    db.getRentalsForSession(session.id));
        } catch (Exception e) {
            Toast.makeText(this, R.string.invoice_error, Toast.LENGTH_SHORT).show();
            return null;
        }
    }
}
