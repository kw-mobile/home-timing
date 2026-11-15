package com.kwmobile.hometiming.ui.calendar;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.google.android.gms.tasks.Task;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.calendar.*;
import java.util.*;

public final class CalendarSettingsViewModel extends AndroidViewModel {
    public final DeviceCalendarManager manager;
    private final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    public List<DeviceCalendarStore.Choice> choices = Collections.emptyList();
    public boolean busy;
    public int message;
    public CalendarSettingsViewModel(@NonNull Application app) { super(app); manager = DeviceCalendarManager.get(app); }
    public LiveData<Integer> changes() { return changes; }
    public void refresh() {
        if (busy) return;
        if (!manager.permitted()) { choices = Collections.emptyList(); changed(); return; }
        busy = true; changed();
        manager.choices().addOnCompleteListener(done -> {
            busy = false;
            if (done.isSuccessful()) { choices = done.getResult(); message = 0; }
            else message = R.string.calendar_failure;
            changed();
        });
    }
    public void enable(int index) { if (!busy && index >= 0 && index < choices.size()) run(manager.enable(choices.get(index))); }
    public void disable() { if (!busy) run(manager.disable()); }
    public void sync() { if (!busy) run(manager.sync()); }
    private void run(Task<Void> task) {
        busy = true; message = 0; changed();
        task.addOnCompleteListener(done -> { busy = false; message = done.isSuccessful() ? R.string.calendar_synced : R.string.calendar_failure; changed(); });
    }
    private void changed() { changes.setValue(changes.getValue() + 1); }
}
