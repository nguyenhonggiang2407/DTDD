package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.CourtSession;
import com.example.courtmatepickleballcourtmanager.model.Promotion;
import com.example.courtmatepickleballcourtmanager.model.RentalItem;
import com.example.courtmatepickleballcourtmanager.model.Service;
import com.example.courtmatepickleballcourtmanager.model.ServiceOrder;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;
import com.example.courtmatepickleballcourtmanager.util.PromotionEngine;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SessionDetailActivity extends ProtectedActivity {

    public static final String EXTRA_SESSION_ID = "session_id";

    private DatabaseHelper db;
    private int sessionId;
    private CourtSession session;
    private TextView tvTitle;
    private TextView tvInfo;
    private TextView tvMoney;
    private TextView tvOrders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        sessionId = getIntent().getIntExtra(EXTRA_SESSION_ID, 0);
        setContentView(R.layout.activity_session_detail);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.session_detail_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvTitle = findViewById(R.id.tvSessionTitle);
        tvInfo = findViewById(R.id.tvSessionInfo);
        tvMoney = findViewById(R.id.tvSessionMoney);
        tvOrders = findViewById(R.id.tvOrders);

        findViewById(R.id.btnAddService).setOnClickListener(v -> addService());
        findViewById(R.id.btnRent).setOnClickListener(v -> rentEquipment());
        findViewById(R.id.btnPromotion).setOnClickListener(v -> applyPromotion());
        findViewById(R.id.btnFinish).setOnClickListener(v -> finishSession());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        session = db.getSession(sessionId);
        if (session == null) {
            finish();
            return;
        }
        int courtFee = session.courtFee;
        if (CourtSession.TYPE_WALK_IN.equals(session.type)) {
            courtFee = DateTimeUtils.estimateSessionFee(session.walkInPrice,
                    session.startTime, DateTimeUtils.nowDbDateTime());
        }
        int serviceFee = 0;
        List<ServiceOrder> orders = db.getServiceOrders(sessionId);
        StringBuilder orderText = new StringBuilder();
        for (ServiceOrder order : orders) {
            serviceFee += order.amount;
            orderText.append("• ").append(order.serviceName).append(" × ")
                    .append(order.quantity).append(" = ")
                    .append(MoneyUtils.format(order.amount)).append("\n");
        }
        if (orderText.length() == 0) {
            orderText.append(getString(R.string.session_order_empty));
        }
        tvOrders.setText(orderText.toString().trim());
        tvTitle.setText(session.courtName == null
                ? getString(R.string.session_detail_title) : session.courtName);
        String customer = session.customerName == null
                ? getString(R.string.session_walk_in_customer) : session.customerName;
        tvInfo.setText(getString(R.string.session_playing,
                DateTimeUtils.displayDateTime(session.startTime)) + "\n"
                + getString(R.string.session_customer, customer));
        int discount = session.discountCourt + session.discountService;
        int total = Math.max(0, courtFee + serviceFee + session.rentalFee - discount);
        tvMoney.setText(getString(R.string.session_fee_breakdown,
                MoneyUtils.format(courtFee), MoneyUtils.format(serviceFee),
                MoneyUtils.format(session.rentalFee), MoneyUtils.format(discount),
                MoneyUtils.format(total)));
    }

    private void addService() {
        List<Service> services = db.getServicesByType(Service.TYPE_SALE);
        if (services.isEmpty()) {
            Toast.makeText(this, R.string.stats_no_data, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> labels = new ArrayList<>();
        for (Service service : services) {
            labels.add(service.name + " • " + MoneyUtils.format(service.price)
                    + "/" + service.unit + " • tồn " + service.stock);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_service_order)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    Service service = services.get(which);
                    askQuantity(service);
                })
                .show();
    }

    private void askQuantity(Service service) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.rental_quantity);
        input.setText("1");
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        layout.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle(service.name)
                .setView(layout)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    int quantity = parseInt(input.getText().toString(), 1);
                    long id = db.addServiceOrder(sessionId, service.id, quantity);
                    if (id > 0) {
                        Toast.makeText(this, R.string.service_order_added,
                                Toast.LENGTH_SHORT).show();
                        reload();
                    } else {
                        Toast.makeText(this, R.string.service_order_error,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void rentEquipment() {
        List<Service> rentals = db.getServicesByType(Service.TYPE_RENTAL);
        if (rentals.isEmpty()) {
            Toast.makeText(this, R.string.stats_no_data, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> labels = new ArrayList<>();
        for (Service service : rentals) {
            labels.add(service.name + " • cọc " + MoneyUtils.format(service.deposit)
                    + " • tồn " + service.stock);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rent_equipment)
                .setItems(labels.toArray(new String[0]), (dialog, which) ->
                        askRental(rentals.get(which)))
                .show();
    }

    private void askRental(Service service) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        EditText quantity = new EditText(this);
        quantity.setHint(R.string.rental_quantity);
        quantity.setInputType(InputType.TYPE_CLASS_NUMBER);
        quantity.setText("1");
        EditText due = new EditText(this);
        due.setHint(R.string.rental_due);
        due.setText(DateTimeUtils.addHoursToDbDateTime(DateTimeUtils.nowDbDateTime(), 4));
        layout.addView(quantity);
        layout.addView(due);
        new MaterialAlertDialogBuilder(this)
                .setTitle(service.name)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    int customerId = session.customerId;
                    if (customerId <= 0) {
                        customerId = askCustomerAndRent(service, quantity, due);
                        return;
                    }
                    createRental(customerId, service, quantity.getText().toString(),
                            due.getText().toString());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int askCustomerAndRent(Service service, EditText quantity, EditText due) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        EditText name = new EditText(this);
        name.setHint(R.string.customer_name_hint);
        EditText phone = new EditText(this);
        phone.setHint(R.string.phone_hint);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        layout.addView(name);
        layout.addView(phone);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.customer_name_hint)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    int customerId = db.findOrCreateCustomer(name.getText().toString(),
                            phone.getText().toString());
                    if (customerId < 0) {
                        Toast.makeText(this, R.string.customer_name_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createRental(customerId, service, quantity.getText().toString(),
                            due.getText().toString());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        return 0;
    }

    private void createRental(int customerId, Service service, String quantityText,
                              String due) {
        int quantity = parseInt(quantityText, 1);
        List<RentalItem> items = new ArrayList<>();
        RentalItem item = new RentalItem();
        item.serviceId = service.id;
        item.quantity = quantity;
        items.add(item);
        long id = db.createRental(customerId, sessionId, DateTimeUtils.nowDbDateTime(),
                due.trim(), items);
        if (id > 0) {
            Toast.makeText(this, R.string.rental_created, Toast.LENGTH_SHORT).show();
            reload();
        } else {
            Toast.makeText(this, R.string.service_order_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void applyPromotion() {
        String date = DateTimeUtils.today();
        List<Promotion> automatic = db.getAutomaticPromotions(date, session.courtId);
        StringBuilder message = new StringBuilder();
        if (automatic.isEmpty()) {
            message.append(getString(R.string.promotion_none));
        } else {
            for (Promotion promotion : automatic) {
                message.append("• ").append(promotion.name).append(" (")
                        .append(PromotionEngine.discountLabel(promotion)).append(")\n");
            }
        }
        EditText code = new EditText(this);
        code.setHint(R.string.promotion_code);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), 0);
        TextView info = new TextView(this);
        info.setText(message.toString().trim());
        layout.addView(info);
        layout.addView(code);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.apply_promotion)
                .setView(layout)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    String codeText = code.getText().toString().trim();
                    Promotion codePromotion = codeText.isEmpty() ? null
                            : db.findPromotionByCode(codeText);
                    int courtFee = session.courtFee;
                    if (CourtSession.TYPE_WALK_IN.equals(session.type)) {
                        courtFee = DateTimeUtils.estimateSessionFee(session.walkInPrice,
                                session.startTime, DateTimeUtils.nowDbDateTime());
                    }
                    int serviceFee = 0;
                    for (ServiceOrder order : db.getServiceOrders(sessionId)) {
                        serviceFee += order.amount;
                    }
                    PromotionEngine.Result result = PromotionEngine.bestForSession(automatic,
                            courtFee, serviceFee, date, codePromotion, codeText);
                    if (!result.hasDiscount()) {
                        Toast.makeText(this, R.string.promotion_not_found,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    db.applySessionDiscount(sessionId, result.promotion, result.code,
                            result.discountCourt, result.discountService);
                    String name = result.promotion == null
                            ? getString(R.string.promotion_title) : result.promotion.name;
                    Toast.makeText(this, getString(R.string.promotion_applied, name,
                            MoneyUtils.format(result.totalDiscount)), Toast.LENGTH_LONG).show();
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void finishSession() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.finish_session)
                .setMessage(R.string.finish_and_pay_confirm)
                .setPositiveButton(R.string.payment_continue, (dialog, which) -> {
                    Intent intent = new Intent(this, PaymentActivity.class);
                    intent.putExtra(PaymentActivity.EXTRA_SESSION_ID, sessionId);
                    intent.putExtra(PaymentActivity.EXTRA_END_TIME,
                            DateTimeUtils.nowDbDateTime());
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
