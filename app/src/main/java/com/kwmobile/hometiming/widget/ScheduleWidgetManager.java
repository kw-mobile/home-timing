package com.kwmobile.hometiming.widget;

import android.content.Context;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.android.gms.tasks.Tasks;
import com.kwmobile.hometiming.data.auth.OperationScope;
import com.kwmobile.hometiming.data.firestore.ItemError;
import com.kwmobile.hometiming.data.firestore.ItemException;
import com.kwmobile.hometiming.data.firestore.ReminderRepository;
import com.kwmobile.hometiming.data.firestore.ReminderSnapshot;
import com.kwmobile.hometiming.domain.Chore;
import com.kwmobile.hometiming.domain.Item;
import com.kwmobile.hometiming.domain.WidgetSchedule;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Serializes local publication and rejects server results superseded by UI or session changes. */
public final class ScheduleWidgetManager {
    private static ScheduleWidgetManager instance;
    private final Context context;
    final ScheduleWidgetStore store;
    private long version;
    private OperationScope.Registration invalidation = () -> { };
    private ScheduleWidgetManager(Context context) {
        this.context = context.getApplicationContext(); store = new ScheduleWidgetStore(this.context);
    }
    public static synchronized ScheduleWidgetManager get(Context context) {
        if (instance == null) instance = new ScheduleWidgetManager(context);
        return instance;
    }
    public synchronized void start(String uid, String house) {
        invalidation.remove(); version++;
        OperationScope.Token scope = OperationScope.getInstance().capture(uid, house);
        if (scope == null) { end(); return; }
        store.bind(uid, house);
        invalidation = OperationScope.getInstance().onInvalidated(scope, this::end);
        render();
    }
    public synchronized void end() {
        version++; invalidation.remove(); invalidation = () -> { };
        store.clear(); render();
    }
    public synchronized void publish(String uid, String house, List<Item> items, List<Chore> chores, boolean cached) {
        if (!uid.equals(store.uid()) || !house.equals(store.house())
                || !OperationScope.getInstance().isCurrent(OperationScope.getInstance().capture(uid, house), liveUid())) return;
        version++; store.save(WidgetSchedule.project(items, chores), cached); render();
    }
    public synchronized void render() {
        if (!store.uid().isEmpty() && !store.uid().equals(liveUid())) { version++; store.clear(); }
        ScheduleWidgetProvider.render(context, store);
    }
    public void requestSync() {
        if (ScheduleWidgetProvider.hasWidgets(context)) WorkManager.getInstance(context).enqueueUniqueWork(
                "schedule_widget_sync", ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(ScheduleWidgetWorker.class).build());
    }
    boolean sync() {
        final String uid, house; final long ticket;
        synchronized (this) {
            render(); uid = store.uid(); house = store.house(); ticket = ++version;
            if (uid.isEmpty()) return true;
        }
        try {
            ReminderSnapshot snapshot = Tasks.await(ReminderRepository.getInstance().readFromServer(uid, house), 35, TimeUnit.SECONDS);
            synchronized (this) {
                if (ticket == version && uid.equals(liveUid()) && house.equals(store.house())) {
                    store.save(WidgetSchedule.project(snapshot.items, snapshot.chores), false); render();
                }
            }
            return true;
        } catch (Exception error) {
            synchronized (this) {
                if (ticket != version) return true;
                Throwable cause = error.getCause();
                if (cause instanceof ItemException && (((ItemException) cause).error == ItemError.PERMISSION_DENIED
                        || ((ItemException) cause).error == ItemError.NOT_SIGNED_IN
                        || ((ItemException) cause).error == ItemError.NOT_FOUND)) { end(); return true; }
                render();
            }
            return false;
        }
    }
    private String liveUid() { return ReminderRepository.getInstance().currentUid(); }
}
