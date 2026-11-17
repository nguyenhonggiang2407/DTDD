package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.courtmatepickleballcourtmanager.security.AuthSession;

/** Applies local session/role checks to every pre-existing Activity. */
public abstract class ProtectedActivity extends AppCompatActivity {
    @Override protected void onStart() {
        super.onStart();
        if (!AuthSession.isLoggedIn()) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        if (!isAllowed()) {
            Toast.makeText(this, "Chức năng chỉ dành cho chủ sân", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        }
    }
    private boolean isAllowed() {
        return com.example.courtmatepickleballcourtmanager.security.RoleAccessPolicy.canOpen(
                AuthSession.role(), getClass().getSimpleName());
    }
}
