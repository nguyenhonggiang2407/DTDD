package com.example.courtmatepickleballcourtmanager.repository;

import android.content.Context;
import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;

/** A small Repository boundary around the atomic checkout write. */
public final class CheckoutRepository {
    private final DatabaseHelper db;
    public CheckoutRepository(Context context) {
        this.db = DatabaseHelper.getInstance(context.getApplicationContext());
    }
    public int complete(int sessionId, String endTime, String method,
                        String transactionId, String paidAt) {
        return db.completeSessionPayment(sessionId, endTime, method, transactionId, paidAt);
    }
}
