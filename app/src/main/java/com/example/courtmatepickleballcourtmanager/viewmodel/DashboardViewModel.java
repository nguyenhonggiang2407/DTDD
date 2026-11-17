package com.example.courtmatepickleballcourtmanager.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.courtmatepickleballcourtmanager.repository.DashboardRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Incremental MVVM extraction for Dashboard; no massive risky rewrite. */
public final class DashboardViewModel extends AndroidViewModel {
    private final DashboardRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<DashboardRepository.Snapshot> snapshot = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    public DashboardViewModel(@NonNull Application app) {
        super(app); repository = new DashboardRepository(app);
    }
    public LiveData<DashboardRepository.Snapshot> data() { return snapshot; }
    public LiveData<String> error() { return error; }
    public void refresh() {
        executor.execute(() -> {
            try { snapshot.postValue(repository.load()); }
            catch (RuntimeException e) { error.postValue("Không thể nạp Dashboard: " + e.getMessage()); }
        });
    }
    @Override protected void onCleared() {
        executor.shutdownNow();
        super.onCleared();
    }
}
