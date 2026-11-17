package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.courtmatepickleballcourtmanager.util.BackupManager;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingsActivity extends ProtectedActivity {

    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String[]> restoreLauncher;
    private TextView tvLastBackup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.settings_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvLastBackup = findViewById(R.id.tvBackupHint);
        String lastBackup = BackupManager.lastBackupAt(this);
        if (lastBackup != null) {
            tvLastBackup.setText(getString(R.string.backup_success_at, lastBackup));
        }

        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"),
                uri -> {
                    if (uri != null) {
                        performBackup(uri);
                    }
                });
        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        confirmRestore(uri);
                    }
                });

        findViewById(R.id.btnBackup).setOnClickListener(v ->
                backupLauncher.launch("courtmate-backup-"
                        + DateTimeUtils.today().replace("-", "") + ".db"));
        findViewById(R.id.btnRestore).setOnClickListener(v ->
                restoreLauncher.launch(new String[]{"*/*"}));
        findViewById(R.id.btnAuditLog).setOnClickListener(v ->
                startActivity(new Intent(this, AuditLogActivity.class)));
        findViewById(R.id.btnPaymentLedger).setOnClickListener(v ->
                startActivity(new Intent(this, PaymentLedgerActivity.class)));
        findViewById(R.id.btnUsers).setOnClickListener(v ->
                startActivity(new Intent(this, UsersActivity.class)));
        findViewById(R.id.btnAbout).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
        findViewById(R.id.btnDemoData).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Bổ sung dữ liệu demo?")
                        .setMessage("Tạo tối thiểu 3 mẫu cho huấn luyện viên, lớp học, sự kiện, khuyến mãi, khung giờ, lượt đặt, thuê đồ và phiên đã thanh toán. Dữ liệu chỉ để thử nghiệm; số liệu tài chính sẽ chứa giao dịch DEMO. Hãy sao lưu nếu đang dùng dữ liệu thật.")
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton("Nạp demo", (dialog, which) -> {
                            new Thread(() -> {
                                try {
                                    int added = com.example.courtmatepickleballcourtmanager.data.DatabaseHelper
                                            .getInstance(this).addDemoData();
                                    runOnUiThread(() -> Toast.makeText(this,
                                            "Đã thêm " + added + " bản ghi demo mới (không nhân đôi).",
                                            Toast.LENGTH_LONG).show());
                                } catch (Exception e) {
                                    android.util.Log.e("CourtMateDemo", "Seed failed", e);
                                    runOnUiThread(() -> Toast.makeText(this,
                                            "Không nạp được demo: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show());
                                }
                            }).start();
                        }).show());
    }

    private void performBackup(Uri uri) {
        try {
            BackupManager.backup(this, uri);
            tvLastBackup.setText(getString(R.string.backup_success_at,
                    DateTimeUtils.nowDbDateTime()));
            Toast.makeText(this, R.string.backup_success, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void confirmRestore(Uri uri) {
        if (!BackupManager.validate(this, uri)) {
            Toast.makeText(this, R.string.restore_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restore_confirm_title)
                .setMessage(R.string.restore_confirm_message)
                .setPositiveButton(R.string.restore_confirm, (dialog, which) -> {
                    try {
                        BackupManager.restore(this, uri);
                        com.example.courtmatepickleballcourtmanager.security.AuthSession.logout();
                        Toast.makeText(this, R.string.restore_success,
                                Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.restore_failed,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
