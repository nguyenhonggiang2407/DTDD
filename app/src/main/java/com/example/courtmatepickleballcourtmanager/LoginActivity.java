package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.courtmatepickleballcourtmanager.security.AuthRepository;
import com.example.courtmatepickleballcourtmanager.security.AuthSession;
import com.google.android.material.button.MaterialButton;

/** First launch creates a real local administrator; subsequent launches require credentials. */
public class LoginActivity extends AppCompatActivity {
    private AuthRepository repository;
    private EditText username, password;
    private MaterialButton submit;
    private boolean setup;
    private SharedPreferences authLimits;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_login);
        repository = new AuthRepository(this);
        authLimits = getSharedPreferences("login_rate_limit", MODE_PRIVATE);
        username = findViewById(R.id.authUsername);
        password = findViewById(R.id.authPassword);
        submit = findViewById(R.id.authSubmit);
        setup = repository.countUsers() == 0;
        ((TextView)findViewById(R.id.authTitle)).setText(setup
                ? "Thiết lập chủ sân" : "Đăng nhập CourtMate");
        ((TextView)findViewById(R.id.authHelp)).setText(setup
                ? "Tạo tài khoản Admin đầu tiên. Mật khẩu tối thiểu 10 ký tự."
                : "Đăng nhập để quản lý sân. Phiên sẽ hết hạn sau 6 giờ.");
        submit.setText(setup ? "Tạo tài khoản quản trị" : "Đăng nhập");
        submit.setOnClickListener(v -> authenticate());
        if (!setup && AuthSession.isLoggedIn()) openDashboard();
    }
    private void authenticate() {
        if (System.currentTimeMillis() < authLimits.getLong("blocked_until", 0)) {
            Toast.makeText(this, "Tài khoản tạm khóa đăng nhập trong 5 phút", Toast.LENGTH_SHORT).show();
            return;
        }
        String user = username.getText().toString();
        char[] secret = password.getText().toString().toCharArray();
        try {
            if (setup) {
                repository.createFirstAdmin(user, secret);
                setup = false;
            }
            if (!repository.signIn(user, secret)) {
                int attempts = authLimits.getInt("attempts", 0) + 1;
                if (attempts >= 5) {
                    authLimits.edit().putLong("blocked_until",
                            System.currentTimeMillis() + 5 * 60_000L).putInt("attempts", 0).apply();
                } else authLimits.edit().putInt("attempts", attempts).apply();
                Toast.makeText(this, "Tên đăng nhập hoặc mật khẩu không đúng", Toast.LENGTH_LONG).show();
                return;
            }
            authLimits.edit().clear().apply();
            openDashboard();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage() == null ? "Không thể đăng nhập" : e.getMessage(),
                    Toast.LENGTH_LONG).show();
        } finally {
            java.util.Arrays.fill(secret, '\0');
            password.setText("");
        }
    }
    private void openDashboard() {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
