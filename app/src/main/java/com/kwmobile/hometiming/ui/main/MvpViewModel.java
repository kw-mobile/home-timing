package com.kwmobile.hometiming.ui.main;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.android.gms.tasks.Task;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.auth.*;
import com.kwmobile.hometiming.data.firestore.*;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.data.weather.Region;
import com.kwmobile.hometiming.domain.*;
import com.kwmobile.hometiming.notify.ReminderManager;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/** Owns the active account/house, remote subscriptions and acknowledged writes. No View references. */
public final class MvpViewModel extends AndroidViewModel {
    private final AuthRepository auth = AuthRepository.getInstance();
    private final ItemRepository items = ItemRepository.getInstance();
    private final ChoreRepository chores = ChoreRepository.getInstance();
    private final HouseRepository houses = HouseRepository.getInstance();
    private final CategoryRepository categories = CategoryRepository.getInstance();
    private final CategoryCatalog[] catalogs = new CategoryCatalog[2];
    private final boolean[] catalogCache = {true, true};
    private final Set<String> adopting = new HashSet<>();
    private final Set<String> adoptionFailed = new HashSet<>();
    public CategoryCatalog catalog(boolean chore) { return catalogs[chore ? 1 : 0]; }
    private final ReminderManager reminders;
    private final com.kwmobile.hometiming.data.calendar.DeviceCalendarManager calendars;
    private final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    private final List<Subscription> subscriptions = new ArrayList<>();
    private Subscription history = Subscription.NONE;
    private final Map<String, List<PreviewStore.History>> histories = new HashMap<>();
    private DesignPreviewViewModel ui;
    private List<Item> itemRows = Collections.emptyList();
    private List<Chore> choreRows = Collections.emptyList();
    private PreviewStore.Item editing;
    private int generation;
    private boolean started, disposed, busy, itemCache = true, choreCache = true, houseCache = true;
    private String historyId = "", message, pendingReminder;
    public Session session;
    public House house;
    public List<Member> members = Collections.emptyList();

    public MvpViewModel(@NonNull Application app) {
        super(app);
        reminders = ReminderManager.getInstance(app);
        calendars = com.kwmobile.hometiming.data.calendar.DeviceCalendarManager.get(app);
    }

    public String notifyTime() { return ui.state.time; }
    public int advanceDays() { return ui.state.advanceDays; }
    public String inviteCode() { return ui.state.invite; }
    public String joinCode() { return ui.state.joinCode; }
    public void joinCode(String value) { ui.state.joinCode = value; }
    public LiveData<Integer> changes() { return changes; }
    public boolean hasStoredAccount() { return auth.isSignedIn(); }
    public boolean busy() { return busy; }
    public boolean readOnly() {
        return session == null || session.offline || itemCache || choreCache || houseCache
                || catalogCache[0] || catalogCache[1] || !adopting.isEmpty() || !adoptionFailed.isEmpty();
    }
    public String takeMessage() { String result = message; message = null; return result; }
    public void attach(DesignPreviewViewModel state) {
        ui = state;
        ui.state.live = true;
        // Saved screen models are never treated as server evidence after process death.
        if (!started) {
            started = true;
            clearData();
            ui.tab("login");
            if (auth.isSignedIn()) restore();
            else if (!auth.isConfigured()) say(R.string.auth_error_not_configured);
        }
    }

    public void openReminder(String kind, String id) {
        if (id == null || id.isEmpty() || id.contains("/") || !("ITEM".equals(kind) || "CHORE".equals(kind))) return;
        pendingReminder = ("ITEM".equals(kind) ? "item:" : "chore:") + id;
        if (session != null) {
            ui.navigate("detail", pendingReminder); pendingReminder = null; changed();
        }
    }

    public void authenticate(String mode, String name, String email, String password) {
        if (busy) return;
        if (mode.equals("reset")) {
            run(auth.sendPasswordReset(email), ignored -> {
                ui.state.authFields.clear(); ui.tab("login"); say(R.string.mvp_reset_sent);
            });
        } else run(mode.equals("signup") ? auth.signUp(name, email, password) : auth.signIn(email, password), this::bind);
    }

    public void restore() {
        if (busy) return;
        run(auth.restoreSession(), this::bind);
    }

    private void bind(Session value) {
        closeSubscriptions();
        clearData();
        session = value;
        ui.state.person = value.uid;
        ui.state.members = 1;
        ui.state.scenario = value.offline ? "offline" : "loading";
        ui.state.authFields.clear();
        ui.state.passwordVisible = false;
        ui.tab("home");
        if (pendingReminder != null) { ui.navigate("detail", pendingReminder); pendingReminder = null; }
        int token = generation;
        subscriptions.add(items.listenItems(value.houseId, new ItemRepository.ItemsListener() {
            public void onItems(@NonNull List<Item> rows, boolean cache) {
                if (!current(token)) return;
                itemRows = rows; itemCache = cache; rebuild();
            }
            public void onError(@NonNull ItemError error) { readError(token, error); }
        }));
        subscriptions.add(chores.listenChores(value.houseId, new ChoreRepository.ChoresListener() {
            public void onChores(@NonNull List<Chore> rows, boolean cache) {
                if (!current(token)) return;
                choreRows = rows; choreCache = cache; rebuild();
            }
            public void onError(@NonNull ItemError error) { readError(token, error); }
        }));
        subscriptions.add(houses.listenHouse(value.houseId, new HouseRepository.HouseListener() {
            public void onHouse(House row, boolean cache) {
                if (!current(token)) return;
                if (row == null && !cache) { recoverMembership(); return; }
                house = row; houseCache = cache;
                if (row != null) {
                    ui.state.owner = row.isOwner(value.uid);
                    ui.state.members = row.memberCount;
                    ui.state.invite = row.activeInviteCode == null ? "" : row.activeInviteCode;
                }
                rebuild();
            }
            public void onError(@NonNull HouseError error) {
                if (!current(token)) return;
                if (error == HouseError.PERMISSION_DENIED || error == HouseError.NOT_FOUND) recoverMembership();
                else say(error.messageRes);
            }
        }));
        subscriptions.add(houses.listenMembers(value.houseId, value.uid, new HouseRepository.MembersListener() {
            public void onMembers(@NonNull List<Member> rows, boolean cache) {
                if (!current(token)) return;
                members = rows; changed();
            }
            public void onError(@NonNull HouseError error) {
                if (current(token) && error != HouseError.PERMISSION_DENIED) say(error.messageRes);
            }
        }));
        for (boolean chore : new boolean[] {false, true}) {
            final int index = chore ? 1 : 0;
            subscriptions.add(categories.listen(value.houseId, chore, new CategoryRepository.Listener() {
                public void onCatalog(CategoryCatalog catalog, boolean cache) {
                    if (!current(token)) return;
                    catalogCache[index] = cache || catalog == null;
                    if (catalog == null && !cache) {
                        categories.initialize(value.houseId, chore).addOnFailureListener(error -> {
                            if (current(token)) categoryFailure(error);
                        });
                    } else if (catalog != null) receiveCatalog(catalog);
                }
                public void onError(Exception error) {
                    if (!current(token)) return;
                    catalogCache[index] = true;
                    categoryFailure(error);
                }
            }));
        }
        reminders.onSessionStarted(value.houseId);
        calendars.start(value.uid, value.houseId);
        ReminderRepository.getInstance().readFromServer().addOnSuccessListener(settings -> {
            if (!current(token) || busy) return;
            ui.state.time = settings.notifyTime;
            ui.state.advanceDays = settings.advanceDays;
            changed();
        });
        changed();
    }

    private void recoverMembership() {
        closeSubscriptions();
        reminders.onSessionEnded(); calendars.end();
        clearData();
        session = null;
        ui.state.draft = null;
        ui.tab("login");
        restore();
    }

    private void readError(int token, ItemError error) {
        if (!current(token)) return;
        if (error == ItemError.PERMISSION_DENIED || error == ItemError.NOT_SIGNED_IN) recoverMembership();
        else { ui.state.scenario = "error"; say(error.messageRes); }
    }

    private void rebuild() {
        ui.state.store.items.clear();
        adoptLegacy();
        for (Item row : itemRows) ui.state.store.items.add(MvpRecords.item(row, catalogs[0]));
        for (Chore row : choreRows) ui.state.store.items.add(MvpRecords.chore(row, PreviewStore.today(), catalogs[1]));
        for (PreviewStore.Item row : ui.state.store.items) {
            List<PreviewStore.History> events = histories.get(row.id);
            if (events != null) row.history.addAll(events);
        }
        ui.state.scenario = readOnly() ? "offline" : "normal";
        changed();
    }

    public void beginEdit(String id) { editing = ui.state.store.find(id); }

    public void saveDraft() {
        if (!writable()) return;
        DesignPreviewViewModel.Draft d = ui.state.draft;
        if (d == null) return;
        // Freeze values and the record shown when editing began, before any asynchronous work.
        String houseId = session.houseId;
        CategoryCatalog catalog = catalog(d.chore);
        String categoryId = catalog == null ? null : catalog.idForName(d.category);
        if (categoryId == null) { say(R.string.category_changed); return; }
        if (d.chore) {
            String type = editing != null && editing.serverChore != null ? editing.serverChore.type
                    : MvpRecords.choreType(CategoryCatalog.defaults(true).labels.getOrDefault(categoryId, "기타"));
            ChoreDraft draft = new ChoreDraft(d.name, type, d.weather,
                    d.weekday, d.date, d.location, d.memo, categoryId, d.category);
            if (!ChoreInputValidator.validateDraft(draft, PreviewStore.today()).isEmpty()) {
                say(R.string.item_error_invalid_input); return;
            }
            if (!d.id.isEmpty() && (editing == null || editing.serverChore == null)) {
                say(R.string.item_error_schedule_changed); return;
            }
            run(d.id.isEmpty() ? chores.create(houseId, draft) : chores.update(houseId, editing.serverChore, draft), row -> {
                replace(row); saved("chore:" + row.id);
            });
        } else {
            int cycle;
            try { cycle = Integer.parseInt(d.cycle); } catch (NumberFormatException error) { say(R.string.item_error_invalid_input); return; }
            ItemDraft draft = new ItemDraft(d.name, d.category, d.date, cycle, d.productName, d.location, d.memo, categoryId);
            if (!ItemInputValidator.validateDraft(draft, PreviewStore.today()).isEmpty()) {
                say(R.string.item_error_invalid_input); return;
            }
            if (!d.id.isEmpty() && (editing == null || editing.serverItem == null)) {
                say(R.string.item_error_schedule_changed); return;
            }
            run(d.id.isEmpty() ? items.create(houseId, draft) : items.update(houseId, editing.serverItem, draft), row -> {
                replace(row); saved("item:" + row.id);
            });
        }
    }

    private void receiveCatalog(CategoryCatalog next) {
        int index = next.chore ? 1 : 0;
        CategoryCatalog before = catalogs[index];
        if (before != null && next.revision < before.revision) return;
        if (before != null) {
            for (DesignPreviewViewModel.Draft draft : new DesignPreviewViewModel.Draft[] {ui.state.draft, ui.state.componentDraft}) {
                if (draft != null && draft.chore == next.chore) {
                    String id = before.idForName(draft.category);
                    if (id != null) draft.category = next.label(id, draft.category);
                }
            }
            if (!next.chore) {
                String id = before.idForName(ui.state.filter);
                if (id != null) ui.state.filter = next.labels.containsKey(id)
                        ? next.labels.get(id) : "전체";
            }
        }
        catalogs[index] = next;
        Set<String> names = next.chore ? ui.state.choreCategories : ui.state.itemCategories;
        names.clear(); names.addAll(next.labels.values());
        rebuild();
    }
    private void adoptLegacy() {
        if (session == null) return;
        for (Item row : itemRows) if (row.categoryId.isEmpty()) adopt(false, row.category);
        for (Chore row : choreRows) if (row.categoryId.isEmpty()) adopt(true, row.type);
    }
    private void adopt(boolean chore, String raw) {
        CategoryCatalog catalog = catalog(chore);
        if (catalog == null || catalogCache[chore ? 1 : 0]) return;
        String id = CategoryCatalog.legacyId(chore, raw), key = (chore ? "chore:" : "item:") + id;
        if (catalog.resolve(id) != null || adopting.contains(key) || adoptionFailed.contains(key)) return;
        adopting.add(key);
        int token = generation;
        categories.adopt(session.houseId, chore, raw).addOnCompleteListener(done -> {
            if (!current(token)) return;
            adopting.remove(key);
            if (done.isSuccessful()) receiveCatalog(done.getResult());
            else { adoptionFailed.add(key); categoryFailure(done.getException()); }
            changed();
        });
    }
    public void changeCategory(CategoryCatalog shown, String oldName, String name, String replacement,
                               Consumer<Boolean> completion) {
        if (!writable() || shown == null) { completion.accept(false); return; }
        String id = oldName == null ? null : shown.idForName(oldName);
        Task<CategoryCatalog> task = oldName == null ? categories.add(session.houseId, shown.chore, name)
                : replacement == null ? categories.rename(session.houseId, shown, id, name)
                : categories.delete(session.houseId, shown, id, shown.idForName(replacement));
        busy = true; int token = generation; changed();
        task.addOnCompleteListener(done -> {
            if (!current(token)) return;
            busy = false;
            if (done.isSuccessful()) receiveCatalog(done.getResult());
            else categoryFailure(done.getException());
            completion.accept(done.isSuccessful()); changed();
        });
    }
    private void categoryFailure(Exception error) {
        String code = error instanceof CategoryException ? ((CategoryException) error).code : "";
        say("DUPLICATE".equals(code) ? R.string.category_duplicate
                : "LAST".equals(code) ? R.string.category_delete_last
                : "INVALID".equals(code) ? R.string.category_invalid
                : "CHANGED".equals(code) ? R.string.category_changed : R.string.category_remote_error);
    }

    private void saved(String id) {
        ui.state.draft = null;
        editing = null;
        ui.state.route = new DesignPreviewViewModel.Route("detail", id);
        ui.state.back.clear();
        ui.state.back.push(new DesignPreviewViewModel.Route("home"));
        say(R.string.mvp_saved);
        reminders.requestSync();
    }

    public void complete(PreviewStore.Item shown, LocalDate date) {
        if (!writable()) return;
        if (shown.serverChore != null) run(chores.complete(session.houseId, shown.serverChore, date), row -> {
            replace(row); completed(shown.id);
        });
        else if (shown.serverItem != null) run(items.complete(session.houseId, shown.serverItem, date), row -> {
            replace(row); completed(shown.id);
        });
        else say(R.string.item_error_schedule_changed);
    }

    private void completed(String id) {
        ui.state.completionDates.remove(id);
        say(R.string.mvp_completed);
        reminders.requestSync();
    }

    public void delete(PreviewStore.Item row) {
        if (!writable()) return;
        Task<Void> task = row.serverChore != null ? chores.delete(session.houseId, row.serverChore.id)
                : row.serverItem != null ? items.delete(session.houseId, row.serverItem.id) : null;
        if (task == null) return;
        run(task, ignored -> {
            itemRows = new ArrayList<>(itemRows);
            choreRows = new ArrayList<>(choreRows);
            itemRows.removeIf(item -> row.id.equals("item:" + item.id));
            choreRows.removeIf(chore -> row.id.equals("chore:" + chore.id));
            histories.remove(row.id);
            ui.tab("home"); rebuild(); reminders.requestSync(); say(R.string.mvp_deleted);
        });
    }

    public void proposal(PreviewStore.Item shown, LocalDate target) {
        if (!writable() || shown.serverChore == null) return;
        Chore row = shown.serverChore;
        run(target == null ? chores.dismissSuggestion(session.houseId, row.id, row.occurrenceId, row.revision)
                : chores.reschedule(session.houseId, row.id, row.occurrenceId, row.revision, target,
                    Schema.Logs.REASON_RAIN_FORECAST), result -> { replace(result); reminders.requestSync(); });
    }

    public void watchHistory(String id) {
        if (id.equals(historyId)) return;
        history.remove(); history = Subscription.NONE; historyId = "";
        PreviewStore.Item row = ui.state.store.find(id);
        if (row == null || session == null) return;
        historyId = id;
        int token = generation;
        if (row.serverItem != null) history = items.listenHistory(session.houseId, row.serverItem.id,
                new ItemRepository.HistoryListener() {
                    public void onHistory(@NonNull List<HistoryEntry> events, boolean cache) {
                        if (!current(token) || !id.equals(historyId)) return;
                        List<PreviewStore.History> list = new ArrayList<>();
                        for (HistoryEntry event : events) list.add(new PreviewStore.History(event.replacedDate, event.completedByName));
                        histories.put(id, list); rebuild();
                    }
                    public void onError(@NonNull ItemError error) { if (id.equals(historyId)) readError(token, error); }
                });
        else if (row.serverChore != null) history = chores.listenLogs(session.houseId, row.serverChore.id,
                new ChoreRepository.LogsListener() {
                    public void onLogs(@NonNull List<ChoreLog> events, boolean cache) {
                        if (!current(token) || !id.equals(historyId)) return;
                        List<PreviewStore.History> list = new ArrayList<>();
                        for (ChoreLog event : events) list.add(new PreviewStore.History(event.date,
                                event.actorName + ("DONE".equals(event.action) ? "" : " · 일정 변경 " + event.fromDate + " → " + event.date), "DONE".equals(event.action)));
                        histories.put(id, list); rebuild();
                    }
                    public void onError(@NonNull ItemError error) { if (id.equals(historyId)) readError(token, error); }
                });
    }

    public void issueInvite() {
        if (writable()) run(houses.issueInvite(session.houseId), invite -> { ui.state.invite = invite.code; });
    }
    public void stopInvite() {
        if (writable()) run(houses.stopInvite(session.houseId), ignored -> { ui.state.invite = ""; });
    }
    public void join(String code) {
        if (!writable()) return;
        Session previous = session;
        // The commit removes access to the old house before its Task callback may arrive.
        // Detach first so the resulting listener denial cannot discard our own successful move.
        closeSubscriptions(); reminders.onSessionEnded(); calendars.end();
        run(houses.join(code), ignored -> changeHouse(), () -> bind(previous));
    }
    public void leave() {
        if (!writable()) return;
        Session previous = session;
        closeSubscriptions(); reminders.onSessionEnded(); calendars.end();
        run(houses.leave(previous.houseId), ignored -> changeHouse(), () -> bind(previous));
    }
    public void removeMember(String uid) {
        if (writable()) run(houses.removeMember(session.houseId, uid), ignored -> { });
    }
    public void region(Region value) {
        if (writable()) run(houses.setRegion(session.houseId, value.name, value.nx, value.ny), ignored -> { });
    }
    public void settings(String time, int advance) {
        if (writable()) run(reminders.updateSettings(time, advance), ignored -> {
            ui.state.time = time; ui.state.advanceDays = advance; say(R.string.mvp_settings_saved);
        });
    }
    public void deleteAccount(String password) {
        if (busy || session == null && !auth.isSignedIn()) return;
        closeSubscriptions(); reminders.onSessionEnded(); calendars.end();
        itemCache = choreCache = houseCache = true;
        ui.state.store.items.clear();
        run(auth.deleteAccount(password), ignored -> signOut());
    }
    public void signOut() {
        if (busy) return;
        closeSubscriptions(); reminders.onSessionEnded(); calendars.end(); auth.signOut(); clearData();
        session = null; pendingReminder = null;
        ui.state.authFields.clear(); ui.state.draft = null; ui.state.passwordVisible = false;
        ui.tab("login"); changed();
    }
    private void changeHouse() {
        closeSubscriptions(); reminders.onSessionEnded(); calendars.end(); clearData(); session = null;
        ui.state.draft = null; ui.tab("login");
        // The acknowledged membership transaction invalidates the old operation scope.
        restore();
    }
    private boolean writable() {
        if (busy) return false;
        if (readOnly()) { say(R.string.item_error_network); return false; }
        return true;
    }
    private <T> void run(Task<T> task, Consumer<T> success) { run(task, success, () -> { }); }

    private <T> void run(Task<T> task, Consumer<T> success, Runnable failure) {
        if (busy) return;
        busy = true;
        int token = generation;
        changed();
        task.addOnCompleteListener(done -> {
            if (!current(token)) return;
            busy = false;
            if (done.isSuccessful()) success.accept(done.getResult());
            else { failure.run(); error(done.getException()); }
            changed();
        });
    }
    private void replace(Item item) {
        List<Item> result = new ArrayList<>(itemRows);
        result.removeIf(old -> old.id.equals(item.id)); result.add(item); itemRows = result; rebuild();
    }
    private void replace(Chore chore) {
        List<Chore> result = new ArrayList<>(choreRows);
        result.removeIf(old -> old.id.equals(chore.id)); result.add(chore); choreRows = result; rebuild();
    }
    private void clearData() {
        itemRows = Collections.emptyList(); choreRows = Collections.emptyList();
        catalogs[0] = catalogs[1] = null; catalogCache[0] = catalogCache[1] = true;
        adopting.clear(); adoptionFailed.clear();
        histories.clear(); house = null; members = Collections.emptyList(); editing = null;
        itemCache = choreCache = houseCache = true;
        ui.state.store.items.clear(); ui.state.scrolls.clear(); ui.state.completionDates.clear();
        ui.state.itemCategories.clear(); ui.state.choreCategories.clear();
        ui.state.removedItemCategories.clear(); ui.state.removedChoreCategories.clear();
        ui.state.owner = false; ui.state.members = 0; ui.state.invite = "";
        ui.state.person = ""; ui.state.joinCode = ""; ui.state.time = "09:00"; ui.state.advanceDays = 3;
        ui.state.filter = "전체"; ui.state.scenario = "loading";
    }
    private void closeSubscriptions() {
        generation++; busy = false;
        for (Subscription subscription : subscriptions) subscription.remove();
        subscriptions.clear(); history.remove(); history = Subscription.NONE; historyId = "";
    }
    private boolean current(int token) { return !disposed && token == generation; }
    private void error(Exception error) {
        int text = error instanceof AuthException ? ((AuthException) error).error.messageRes
                : error instanceof HouseException ? ((HouseException) error).error.messageRes
                : error instanceof ItemException ? ((ItemException) error).error.messageRes : R.string.item_error_unknown;
        say(text);
    }
    private void say(int text) { message = getApplication().getString(text); changed(); }
    private void changed() { changes.setValue(changes.getValue() + 1); }
    @Override protected void onCleared() { disposed = true; closeSubscriptions(); }
}
