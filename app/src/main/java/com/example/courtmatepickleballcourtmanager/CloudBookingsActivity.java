package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.example.courtmatepickleballcourtmanager.repository.CloudBookingRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cloud schedule operations, separate from offline SQLite management. Admin-only pilot. */
public final class CloudBookingsActivity extends ProtectedActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CloudBookingRepository cloud;
    private EditText url, token;
    private TextView status;
    private LinearLayout list, courtList;
    private TextView summary;
    private MaterialButton add;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_cloud_bookings);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("CourtMate Cloud · Pilot");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        url = findViewById(R.id.cloudUrl);
        token = findViewById(R.id.cloudToken);
        status = findViewById(R.id.cloudStatus);
        list = findViewById(R.id.cloudList);
        courtList = findViewById(R.id.cloudCourtList);
        summary = findViewById(R.id.cloudSummary);
        add = findViewById(R.id.cloudAddBooking);
        findViewById(R.id.cloudConnect).setOnClickListener(v -> connect());
        findViewById(R.id.cloudRefresh).setOnClickListener(v -> refresh());
        add.setOnClickListener(v -> addBooking());
        findViewById(R.id.cloudAddCourt).setOnClickListener(v -> addCourt());
        findViewById(R.id.cloudAddCourt).setEnabled(false);
        add.setEnabled(false);
    }
    private void connect() {
        try {
            cloud = new CloudBookingRepository(url.getText().toString(), token.getText().toString());
            token.setText(""); // Token remains only in the repository memory, not preferences.
            runNetwork(() -> {
                JSONArray courts = cloud.courts();
                runOnUiThread(() -> {
                    add.setEnabled(true);
                    findViewById(R.id.cloudAddCourt).setEnabled(true);
                    status.setText("Đã kết nối HTTPS • " + courts.length() + " sân trên máy chủ");
                });
                loadRemote();
            });
        } catch (Exception e) {
            status.setText(e.getMessage());
        }
    }
    private void refresh() {
        if (cloud == null) { status.setText("Cần kết nối máy chủ trước"); return; }
        runNetwork(this::loadRemote);
    }
    private void loadRemote() throws Exception {
        JSONArray courts = cloud.courts();
        JSONObject metrics = cloud.summary();
        JSONArray records = cloud.bookings();
        runOnUiThread(() -> {
            summary.setText("Hôm nay " + metrics.optString("date")
                    + " • " + metrics.optInt("active_courts") + " sân hoạt động"
                    + "\nChờ xác nhận: " + metrics.optInt("pending")
                    + " • Đã xác nhận: " + metrics.optInt("confirmed")
                    + " • Đã hủy: " + metrics.optInt("cancelled")
                    + "\nThực thu (cloud): " + metrics.optLong("paid_amount")
                    + " đ • Chưa thu: " + metrics.optLong("amount_to_collect") + " đ");
            courtList.removeAllViews();
            for (int i = 0; i < courts.length(); i++) {
                JSONObject item = courts.optJSONObject(i);
                if (item != null) renderCourt(item);
            }
            list.removeAllViews();
            if (records.length() == 0) {
                TextView empty = new TextView(this);
                empty.setText("Chưa có lịch online. Lịch offline không đồng bộ ở phiên bản này.");
                list.addView(empty);
            }
            for (int i=0;i<records.length();i++) {
                JSONObject o = records.optJSONObject(i);
                if (o == null) continue;
                renderRow(o);
            }
            status.setText("Đã tải " + records.length() + " lịch từ máy chủ • Không phải lịch SQLite cục bộ");
        });
    }
    private void renderCourt(JSONObject court) {
        int id = court.optInt("id");
        boolean active = court.optInt("active") == 1;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(10));
        box.setBackgroundResource(R.drawable.bg_cloud_booking);
        LinearLayout.LayoutParams margins = new LinearLayout.LayoutParams(-1, -2);
        margins.bottomMargin = dp(10);
        courtList.addView(box, margins);
        TextView name = new TextView(this);
        name.setText(court.optString("name") + " • " + court.optLong("price_hour")
                + " đ/giờ\n" + (active ? "Đang nhận lịch online" : "Tạm đóng nhận lịch mới"));
        name.setTextSize(14f);
        box.addView(name);
        MaterialButton toggle = actionButton(active ? "Tạm đóng sân" : "Mở nhận lịch");
        box.addView(toggle);
        toggle.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(active ? "Tạm đóng sân trên cloud?" : "Mở nhận lịch trên cloud?")
                .setMessage("Lịch đã đặt sẽ được giữ nguyên. Chỉ thay đổi khả năng đặt mới.")
                .setPositiveButton("Xác nhận", (dialog, which) -> runNetwork(() -> {
                    cloud.updateCourtActive(id, !active);
                    loadRemote();
                }))
                .setNegativeButton("Quay lại", null).show());
    }

    private void renderRow(JSONObject o) {
        int id = o.optInt("id");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams margins = new LinearLayout.LayoutParams(-1,-2);
        margins.bottomMargin = dp(12);
        list.addView(box,margins);
        box.setBackgroundResource(R.drawable.bg_cloud_booking);
        TextView details = new TextView(this);
        details.setText("#"+id+"  •  "+o.optString("court_name")+"\n"
                +o.optString("date")+"  "+o.optString("start")+"–"+o.optString("end")+"\n"
                +o.optString("customer_name")+"  •  "+o.optString("customer_phone")+"\n"
                +o.optInt("amount")+" đ  •  "+o.optString("status")
                +"  •  "+o.optString("payment_status"));
        details.setTextSize(14f);
        box.addView(details);
        if ("PENDING".equals(o.optString("status"))) {
            MaterialButton accept = actionButton("Xác nhận lịch");
            box.addView(accept);
            accept.setOnClickListener(v -> changeStatus(id,"CONFIRMED"));
        }
        if (!"CANCELLED".equals(o.optString("status"))
                && !"PAID".equals(o.optString("payment_status"))) {
            MaterialButton cancel = actionButton("Hủy lịch");
            box.addView(cancel);
            cancel.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle("Hủy lịch #" + id).setMessage("Xác nhận hủy lịch trên máy chủ?")
                    .setPositiveButton("Hủy lịch", (dialog,which) -> changeStatus(id,"CANCELLED"))
                    .setNegativeButton("Quay lại",null).show());
        }
        if ("CONFIRMED".equals(o.optString("status"))
                && !"PAID".equals(o.optString("payment_status"))) {
            MaterialButton pay = actionButton("Tạo link thanh toán đối tác (nếu đã kết nối)");
            box.addView(pay);
            pay.setOnClickListener(v -> runNetwork(() -> {
                JSONObject result = cloud.bankIntent(id);
                String paymentUrl = result.getString("payment_url");
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle("Yêu cầu thanh toán #" + id)
                        .setMessage("Số tiền: " + result.optInt("amount") + " đ. "
                                + "Chỉ webhook đã xác minh mới xác nhận tiền trên máy chủ.")
                        .setPositiveButton("Mở trang đối tác", (dialog,which) ->
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))))
                        .setNegativeButton("Đóng",null).show());
            }));
        }
    }
    private MaterialButton actionButton(String text) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        return button;
    }
    private void changeStatus(int id,String state) {
        runNetwork(() -> { cloud.updateStatus(id,state); loadRemote(); });
    }
    private void addCourt() {
        if (cloud == null) { status.setText("Cần kết nối máy chủ trước"); return; }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18),dp(8),dp(18),dp(4));
        EditText name = new EditText(this); name.setHint("Tên sân"); form.addView(name);
        EditText price = new EditText(this); price.setHint("Giá mỗi giờ (VND)");
        price.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        form.addView(price);
        new MaterialAlertDialogBuilder(this).setTitle("Thêm sân lên máy chủ")
                .setView(form).setPositiveButton("Thêm sân",(dialog,which) -> {
                    try {
                        String courtName = name.getText().toString().trim();
                        int hourPrice = Integer.parseInt(price.getText().toString().trim());
                        runNetwork(() -> {
                            cloud.createCourt(courtName,hourPrice);
                            loadRemote();
                        });
                    } catch (NumberFormatException e) { status.setText("Giá sân không hợp lệ"); }
                }).setNegativeButton("Hủy",null).show();
    }
    private void addBooking() {
        runNetwork(() -> {
            JSONArray courts = cloud.courts();
            runOnUiThread(() -> formBooking(courts));
        });
    }
    private void formBooking(JSONArray courts) {
        if (courts.length() == 0) { status.setText("Hãy thêm sân trên máy chủ trước"); return; }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18),dp(8),dp(18),dp(4));
        String[] labels = {"Mã sân (xem danh sách)","Ngày YYYY-MM-DD", "Bắt đầu HH:mm",
                "Kết thúc HH:mm", "Tên khách", "Số điện thoại"};
        String[] values = {""+courts.optJSONObject(0).optInt("id"),
                com.example.courtmatepickleballcourtmanager.util.DateTimeUtils.today(),
                "18:00","19:00","",""};
        EditText[] entries = new EditText[labels.length];
        for (int i=0;i<labels.length;i++) {
            entries[i] = new EditText(this);
            entries[i].setSingleLine(true);
            entries[i].setHint(labels[i]);
            entries[i].setText(values[i]);
            form.addView(entries[i]);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Tạo lịch online")
                .setView(form)
                .setPositiveButton("Lưu lên máy chủ", (dialog,which) -> {
                    try {
                        JSONObject order = new JSONObject()
                                .put("court_id", Integer.parseInt(entries[0].getText().toString().trim()))
                                .put("date", entries[1].getText().toString().trim())
                                .put("start", entries[2].getText().toString().trim())
                                .put("end", entries[3].getText().toString().trim())
                                .put("customer_name", entries[4].getText().toString().trim())
                                .put("customer_phone", entries[5].getText().toString().trim());
                        runNetwork(() -> { cloud.create(order); loadRemote(); });
                    } catch (Exception e) { status.setText(e.getMessage()); }
                })
                .setNegativeButton("Hủy",null).show();
    }
    private interface Work { void run() throws Exception; }
    private void runNetwork(Work work) {
        status.setText("Đang kết nối máy chủ...");
        executor.execute(() -> {
            try { work.run(); }
            catch (Exception e) {
                runOnUiThread(() -> status.setText("Lỗi: " + e.getMessage()));
            }
        });
    }
    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density*n); }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
