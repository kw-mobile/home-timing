package com.kwmobile.hometiming.data.calendar;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.*;
import com.google.android.gms.tasks.*;
import com.kwmobile.hometiming.data.auth.OperationScope;
import com.kwmobile.hometiming.data.firestore.*;
import com.kwmobile.hometiming.domain.CalendarEntry;
import java.util.*;
import java.util.concurrent.*;

/** Device/account opt-in. One serialized provider writer; no network failure is treated as an empty house. */
public final class DeviceCalendarManager {
    private static DeviceCalendarManager instance;
    private static final String WORK = "device_calendar_sync";
    private final Context context;
    private final SharedPreferences prefs;
    private final DeviceCalendarStore store;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private OperationScope.Registration invalidation = () -> { };
    private Subscription changes = Subscription.NONE;
    private int generation;
    private DeviceCalendarManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences("device_calendar", Context.MODE_PRIVATE);
        store = new DeviceCalendarStore(context);
    }
    public static synchronized DeviceCalendarManager get(Context context) {
        if (instance == null) instance = new DeviceCalendarManager(context);
        return instance;
    }
    public boolean permitted() { return store.permitted(); }
    public boolean enabled() { return prefs.getBoolean("enabled", false); }
    public String label() { return prefs.getString("label", ""); }
    public boolean cleanupPending() { return !prefs.getStringSet("owned", Collections.emptySet()).isEmpty() && !enabled(); }
    public Task<List<DeviceCalendarStore.Choice>> choices() { return Tasks.call(writer, store::choices); }

    public void start(String uid, String house) {
        synchronized (lock) {
            changes.remove(); invalidation.remove();
            if (enabled() && (!uid.equals(prefs.getString("uid", "")) || !house.equals(prefs.getString("house", "")))) disable();
            OperationScope.Token scope = OperationScope.getInstance().capture(uid, house);
            if (scope == null) { disable(); return; }
            invalidation = OperationScope.getInstance().onInvalidated(scope, () -> disable());
            Subscription itemChanges = ItemRepository.getInstance().listenItems(house, new ItemRepository.ItemsListener() {
                public void onItems(List<com.kwmobile.hometiming.domain.Item> rows, boolean cache) { if (!cache) sync(); }
                public void onError(ItemError error) { if (error == ItemError.PERMISSION_DENIED) disable(); }
            });
            Subscription choreChanges = ChoreRepository.getInstance().listenChores(house, new ChoreRepository.ChoresListener() {
                public void onChores(List<com.kwmobile.hometiming.domain.Chore> rows, boolean cache) { if (!cache) sync(); }
                public void onError(ItemError error) { if (error == ItemError.PERMISSION_DENIED) disable(); }
            });
            changes = () -> { itemChanges.remove(); choreChanges.remove(); };
            if (enabled()) schedule();
        }
        sync();
    }
    public void end() {
        synchronized (lock) { changes.remove(); invalidation.remove(); changes = Subscription.NONE; invalidation = () -> { }; }
        disable();
    }
    public Task<Void> enable(DeviceCalendarStore.Choice choice) {
        String uid = ReminderRepository.getInstance().currentUid();
        OperationScope.Token scope = OperationScope.getInstance().capture(uid);
        if (scope == null) return Tasks.forException(new IllegalStateException("Sign in required"));
        return Tasks.call(writer, () -> {
            synchronized (lock) {
                if (!OperationScope.getInstance().isCurrent(scope, ReminderRepository.getInstance().currentUid()))
                    throw new IllegalStateException("Session changed");
                if (!store.permitted()) throw new SecurityException("Calendar permission required");
                boolean available = store.choices().stream().anyMatch(c -> c.id == choice.id);
                if (!available) throw new IllegalStateException("Calendar no longer writable");
                generation++;
                prefs.edit().putBoolean("enabled", false).apply();
                cleanup();
                String token = UUID.randomUUID().toString();
                Set<String> owned = new HashSet<>(); owned.add(choice.id + "|" + token);
                prefs.edit().putStringSet("owned", owned).putLong("calendar", choice.id).putString("token", token)
                        .putString("uid", uid).putString("house", scope.houseId).putString("label", choice.label)
                        .putBoolean("enabled", true).apply();
                schedule();
            }
            return null;
        }).onSuccessTask(ignored -> sync());
    }
    public Task<Void> disable() {
        synchronized (lock) {
            generation++;
            prefs.edit().putBoolean("enabled", false).remove("uid").remove("house").remove("label").apply();
            WorkManager.getInstance(context).cancelUniqueWork(WORK);
        }
        return Tasks.call(writer, () -> { synchronized (lock) { cleanup(); } return null; });
    }
    private void cleanup() {
        Set<String> pending = new HashSet<>(prefs.getStringSet("owned", Collections.emptySet()));
        if (pending.isEmpty()) return;
        if (!store.permitted()) throw new SecurityException("Cleanup needs calendar permission");
        for (String binding : new HashSet<>(pending)) {
            String[] parts = binding.split("\\|", 2);
            store.reconcile(Long.parseLong(parts[0]), marker(parts[1]), Collections.emptyList());
            pending.remove(binding);
            prefs.edit().putStringSet("owned", pending).apply();
        }
    }
    private static String marker(String token) { return "HomeTiming/" + token + "/"; }
    public Task<Void> sync() {
        final String uid, house, token; final long calendar; final int version;
        synchronized (lock) {
            if (!enabled()) return Tasks.call(writer, () -> { synchronized (lock) { cleanup(); } return null; });
            uid = prefs.getString("uid", ""); house = prefs.getString("house", "");
            token = prefs.getString("token", ""); calendar = prefs.getLong("calendar", -1); version = generation;
            if (!uid.equals(ReminderRepository.getInstance().currentUid())) return disable();
        }
        return Tasks.call(writer, () -> {
            synchronized (lock) {
                if (version != generation || !enabled() || !uid.equals(ReminderRepository.getInstance().currentUid())) return null;
            }
            final ReminderSnapshot rows;
            try {
                // Serialize full reads as well as writes: an older network response cannot overwrite a newer projection.
                rows = Tasks.await(ReminderRepository.getInstance().readFromServer(uid, house), 35, TimeUnit.SECONDS);
            } catch (Exception error) {
                Throwable cause = error.getCause();
                if (cause instanceof ItemException && (((ItemException) cause).error == ItemError.PERMISSION_DENIED
                        || ((ItemException) cause).error == ItemError.NOT_SIGNED_IN)) disable();
                throw error;
            }
            synchronized (lock) {
                if (version != generation || !enabled() || !uid.equals(ReminderRepository.getInstance().currentUid())) return null;
                if (!store.permitted()) throw new SecurityException("Calendar permission required");
                if (store.choices().stream().noneMatch(c -> c.id == calendar)) throw new IllegalStateException("Calendar unavailable");
                store.reconcile(calendar, marker(token), CalendarEntry.project(rows.items, rows.chores));
            }
            return null;
        });
    }
    private void schedule() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(DeviceCalendarWorker.class, 12, TimeUnit.HOURS)
                        .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
    }
}
