package com.example.courtmatepickleballcourtmanager;

import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.example.courtmatepickleballcourtmanager.security.AuthRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Admin-only staff provisioning; no password reset without administrator account. */
public class UsersActivity extends ProtectedActivity {
    private AuthRepository repository;
    private LinearLayout accounts;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_users);
        repository = new AuthRepository(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Tài khoản & phân quyền");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        accounts = findViewById(R.id.usersContainer);
        findViewById(R.id.btnAddStaff).setOnClickListener(v -> addStaff());
        reload();
    }
    private void reload() {
        accounts.removeAllViews();
        for (AuthRepository.Account a : repository.accounts()) {
            MaterialButton button = new MaterialButton(this);
            button.setText(a.username + "  •  " + a.role
                    + (a.active ? "  •  Đang hoạt động" : "  •  Đã khóa"));
            accounts.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if ("STAFF".equals(a.role)) {
                button.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                        .setTitle(a.username)
                        .setMessage(a.active ? "Khóa tài khoản nhân viên?" : "Mở khóa tài khoản?")
                        .setPositiveButton("Xác nhận", (d,w) -> {
                            repository.setStaffActive(a.id, !a.active); reload();
                        }).setNegativeButton("Hủy", null).show());
            } else button.setEnabled(false);
        }
    }
    private void addStaff() {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(18), dp(12), dp(18), dp(4));
        form.setOrientation(LinearLayout.VERTICAL);
        EditText user = new EditText(this);
        user.setHint("Tên đăng nhập nhân viên");
        form.addView(user);
        EditText password = new EditText(this);
        password.setHint("Mật khẩu tối thiểu 10 ký tự");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(password);
        new MaterialAlertDialogBuilder(this).setTitle("Thêm nhân viên")
                .setView(form).setPositiveButton("Tạo tài khoản", (d,w) -> {
                    char[] secret = password.getText().toString().toCharArray();
                    try { repository.createStaff(user.getText().toString(), secret); reload(); }
                    catch (Exception e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
                    finally { java.util.Arrays.fill(secret, '\0'); }
                }).setNegativeButton("Hủy", null).show();
    }
    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }
}
