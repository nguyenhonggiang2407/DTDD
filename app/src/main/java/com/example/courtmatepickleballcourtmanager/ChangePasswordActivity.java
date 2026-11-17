package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import com.example.courtmatepickleballcourtmanager.security.AuthRepository;
import com.google.android.material.appbar.MaterialToolbar;

/** All signed-in users may rotate their own local password. */
public class ChangePasswordActivity extends ProtectedActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Đổi mật khẩu cá nhân");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        findViewById(R.id.changePasswordSubmit).setOnClickListener(v -> {
            EditText current = findViewById(R.id.currentPassword);
            EditText updated = findViewById(R.id.newPassword);
            char[] oldPassword = current.getText().toString().toCharArray();
            char[] newPassword = updated.getText().toString().toCharArray();
            try {
                new AuthRepository(this).changeMyPassword(oldPassword,newPassword);
                Toast.makeText(this, "Đã đổi mật khẩu. Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, LoginActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
            } catch (Exception e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            } finally {
                java.util.Arrays.fill(oldPassword, '\0');
                java.util.Arrays.fill(newPassword, '\0');
                current.setText(""); updated.setText("");
            }
        });
    }
}
