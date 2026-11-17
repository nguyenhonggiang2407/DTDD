package com.example.courtmatepickleballcourtmanager;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.courtmatepickleballcourtmanager.viewmodel.CheckoutViewModel;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.CourtSession;
import com.example.courtmatepickleballcourtmanager.model.Rental;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.InvoiceGenerator;
import com.example.courtmatepickleballcourtmanager.util.LightController;
import com.example.courtmatepickleballcourtmanager.util.LightScheduler;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.example.courtmatepickleballcourtmanager.util.QRCodeUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;

public class PaymentActivity extends ProtectedActivity {

    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_END_TIME = "end_time";

    private DatabaseHelper db;
    private CheckoutViewModel checkoutViewModel;
    private int sessionId;
    private String endTime;
    private CourtSession session;
    private int total;
    private boolean paid;
    private String qrContent;
    private File invoiceFile;

    private TextView tvPaymentSession;
    private TextView tvPaymentBreakdown;
    private TextView tvPaymentAmount;
    private TextView tvMomoStatus;
    private TextView tvPaymentResult;
    private ImageView ivMomoQr;
    private ProgressBar progressPayment;
    private View panelMomo;
    private View panelResult;
    private MaterialButton btnPay;
    private MaterialButton btnCheckPayment;
    private MaterialButton btnViewInvoice;
    private MaterialButton btnShareInvoice;
    private MaterialButtonToggleGroup toggleMethod;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        sessionId = getIntent().getIntExtra(EXTRA_SESSION_ID, 0);
        endTime = getIntent().getStringExtra(EXTRA_END_TIME);
        if (endTime == null || endTime.trim().isEmpty()) {
            endTime = DateTimeUtils.nowDbDateTime();
        }
        setContentView(R.layout.activity_payment);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.payment_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvPaymentSession = findViewById(R.id.tvPaymentSession);
        tvPaymentBreakdown = findViewById(R.id.tvPaymentBreakdown);
        tvPaymentAmount = findViewById(R.id.tvPaymentAmount);
        tvMomoStatus = findViewById(R.id.tvMomoStatus);
        tvPaymentResult = findViewById(R.id.tvPaymentResult);
        ivMomoQr = findViewById(R.id.ivMomoQr);
        progressPayment = findViewById(R.id.progressPayment);
        panelMomo = findViewById(R.id.panelMomo);
        panelResult = findViewById(R.id.panelResult);
        btnPay = findViewById(R.id.btnPay);
        btnCheckPayment = findViewById(R.id.btnCheckPayment);
        btnViewInvoice = findViewById(R.id.btnViewInvoice);
        btnShareInvoice = findViewById(R.id.btnShareInvoice);
        toggleMethod = findViewById(R.id.toggleMethod);

        toggleMethod.check(R.id.btnMethodCash);
        toggleMethod.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || paid) {
                return;
            }
            if (checkedId == R.id.btnMethodQr) {
                showInternalQr();
            } else {
                panelMomo.setVisibility(View.GONE);
            }
        });

        btnPay.setOnClickListener(v -> {
            if (toggleMethod.getCheckedButtonId() == R.id.btnMethodQr) {
                showInternalQr();
            } else {
                confirmCashPayment();
            }
        });
        btnCheckPayment.setOnClickListener(v -> confirmQrPayment());
        btnViewInvoice.setOnClickListener(v -> openInvoice());
        btnShareInvoice.setOnClickListener(v -> shareInvoice());
        checkoutViewModel = new ViewModelProvider(this).get(CheckoutViewModel.class);
        checkoutViewModel.result().observe(this, result -> {
            if (result == null || !result.consumeOnce()) return;
            setBusy(false);
            if (!result.success) {
                showError(getString(R.string.payment_save_error));
                reload();
                return;
            }
            LightScheduler.refreshCourt(this, result.courtId);
            LightController.turnOff(this, result.courtId, null);
            paid = true;
            panelMomo.setVisibility(View.GONE);
            reload();
        });

        reload();
    }

    private void reload() {
        session = db.getSession(sessionId);
        if (session == null) {
            Toast.makeText(this, R.string.payment_session_missing, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        total = CourtSession.STATUS_FINISHED.equals(session.status)
                ? session.total : db.previewSessionTotal(sessionId, endTime);
        paid = CourtSession.PAYMENT_PAID.equals(session.paymentStatus);

        tvPaymentSession.setText(session.courtName == null
                ? getString(R.string.payment_title) : session.courtName);
        String customer = session.customerName == null
                ? getString(R.string.session_walk_in_customer) : session.customerName;
        long serviceFee = 0L;
        for (com.example.courtmatepickleballcourtmanager.model.ServiceOrder order
                : db.getServiceOrders(sessionId)) {
            serviceFee += order.amount;
        }
        int courtFee = session.courtFee;
        if (CourtSession.TYPE_WALK_IN.equals(session.type)
                && CourtSession.STATUS_PLAYING.equals(session.status)) {
            courtFee = DateTimeUtils.estimateSessionFee(session.walkInPrice,
                    session.startTime, endTime);
        }
        int discount = session.discountCourt + session.discountService;
        tvPaymentBreakdown.setText(customer + "\n"
                + getString(R.string.session_fee_breakdown,
                MoneyUtils.format(courtFee), MoneyUtils.format(serviceFee),
                MoneyUtils.format(session.rentalFee), MoneyUtils.format(discount),
                MoneyUtils.format(total)));
        tvPaymentAmount.setText(MoneyUtils.format(total));

        if (paid) {
            showPaidResult();
        } else {
            panelResult.setVisibility(View.GONE);
            btnPay.setVisibility(View.VISIBLE);
            toggleMethod.setVisibility(View.VISIBLE);
        }
    }

    private void confirmCashPayment() {
        if (total < 0) {
            Toast.makeText(this, R.string.payment_amount_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.payment_cash_confirm_title)
                .setMessage(getString(R.string.payment_cash_confirm_message,
                        MoneyUtils.format(total)))
                .setPositiveButton(R.string.payment_confirm, (dialog, which) ->
                        completePayment(CourtSession.METHOD_CASH, null))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showInternalQr() {
        if (total < 0) {
            Toast.makeText(this, R.string.payment_amount_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        qrContent = "COURTMATE|SESSION=" + sessionId
                + "|AMOUNT=" + total
                + "|COURT=" + (session.courtName == null ? "" : session.courtName)
                + "|TIME=" + DateTimeUtils.nowDbDateTime();
        Bitmap bitmap = QRCodeUtils.generate(qrContent, 720);
        if (bitmap == null) {
            showError(getString(R.string.payment_qr_error));
            return;
        }
        ivMomoQr.setImageBitmap(bitmap);
        tvMomoStatus.setText(getString(R.string.payment_scan_hint, qrContent));
        panelMomo.setVisibility(View.VISIBLE);
        btnPay.setVisibility(View.GONE);
    }

    private void confirmQrPayment() {
        if (paid || session == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.payment_qr_confirm_title)
                .setMessage(getString(R.string.payment_qr_confirm_message,
                        MoneyUtils.format(total)))
                .setPositiveButton(R.string.payment_qr_confirm, (dialog, which) ->
                        completePayment(CourtSession.METHOD_QR, qrContent))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void completePayment(String method, String transactionId) {
        if (paid || session == null) {
            return;
        }
        setBusy(true);
        if (!checkoutViewModel.complete(sessionId, session.courtId, endTime,
                method, transactionId, DateTimeUtils.nowDbDateTime())) {
            setBusy(false);
            showError("Thanh toán đang được xử lý");
        }
    }

    private void showPaidResult() {
        panelResult.setVisibility(View.VISIBLE);
        btnPay.setVisibility(View.GONE);
        toggleMethod.setVisibility(View.GONE);
        tvPaymentResult.setText(getString(R.string.payment_success,
                session.invoiceNo == null ? "" : session.invoiceNo));
        if (session.total != total) {
            total = session.total;
            tvPaymentAmount.setText(MoneyUtils.format(total));
        }
    }

    private void setBusy(boolean busy) {
        progressPayment.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnPay.setEnabled(!busy);
        btnCheckPayment.setEnabled(!busy);
        toggleMethod.setEnabled(!busy);
    }

    private void showError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.payment_error_title)
                .setMessage(message == null ? getString(R.string.error_generic) : message)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private File invoicePdf() {
        if (invoiceFile != null && invoiceFile.exists()) {
            return invoiceFile;
        }
        try {
            session = db.getSession(sessionId);
            List<Rental> rentals = db.getRentalsForSession(sessionId);
            invoiceFile = InvoiceGenerator.createPdf(this, session,
                    db.getServiceOrders(sessionId), rentals);
            return invoiceFile;
        } catch (Exception e) {
            showError(getString(R.string.invoice_error));
            return null;
        }
    }

    private void openInvoice() {
        File file = invoicePdf();
        if (file != null) {
            InvoiceGenerator.open(this, file);
        }
    }

    private void shareInvoice() {
        File file = invoicePdf();
        if (file != null) {
            InvoiceGenerator.share(this, file);
        }
    }
}
