package com.example.courtmatepickleballcourtmanager.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.courtmatepickleballcourtmanager.repository.CheckoutRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Handles SQLite payment on a worker thread, exposing one-time result to the UI. */
public final class CheckoutViewModel extends AndroidViewModel {
    public static final class Result {
        public final boolean success;
        public final int courtId;
        private boolean consumed;
        public Result(boolean success, int courtId) {
            this.success=success; this.courtId=courtId;
        }
        public synchronized boolean consumeOnce() {
            if (consumed) return false;
            consumed = true;
            return true;
        }
    }
    private final CheckoutRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean working = new AtomicBoolean(false);
    private final MutableLiveData<Result> result = new MutableLiveData<>();
    public CheckoutViewModel(@NonNull Application app) {
        super(app);
        repository = new CheckoutRepository(app);
    }
    public LiveData<Result> result() { return result; }
    public boolean complete(int sessionId, int courtId, String endTime, String method,
                            String transactionId, String paidAt) {
        if (!working.compareAndSet(false,true)) return false;
        executor.execute(() -> {
            int updated = 0;
            try { updated=repository.complete(sessionId,endTime,method,transactionId,paidAt); }
            catch (RuntimeException ignored) { /* surface failure; database method logs details */ }
            finally { working.set(false); }
            result.postValue(new Result(updated == 1, courtId));
        });
        return true;
    }
    @Override protected void onCleared() { executor.shutdown(); super.onCleared(); }
}
