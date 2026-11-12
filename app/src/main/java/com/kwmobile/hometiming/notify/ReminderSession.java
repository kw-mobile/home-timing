package com.kwmobile.hometiming.notify;

import com.kwmobile.hometiming.domain.PlannedReminder;
import com.kwmobile.hometiming.domain.ReminderChange;
import com.kwmobile.hometiming.domain.ReminderDecision;
import com.kwmobile.hometiming.domain.ReminderKey;

import java.util.List;
import java.time.Clock;
import com.kwmobile.hometiming.domain.ReminderPlanner;
import java.util.Objects;
import java.util.function.Supplier;

/** Account/house ownership of the ledger and the asynchronous work allowed to change it. */
final class ReminderSession {
    interface BindingStore {
        String uid();
        String houseId();
        void save(String uid, String houseId);
    }

    static final class Token {
        final String uid;
        final String houseId;
        final long generation;

        Token(String uid, String houseId, long generation) {
            this.uid = uid;
            this.houseId = houseId;
            this.generation = generation;
        }
    }

    static final class Sync {
        final Token session;
        final long sequence;

        Sync(Token session, long sequence) {
            this.session = session;
            this.sequence = sequence;
        }
    }

    static final class Pending {
        final Token session;
        final PlannedReminder reminder;

        Pending(Token session, PlannedReminder reminder) {
            this.session = session;
            this.reminder = reminder;
        }
    }

    private final ReminderEngine engine;
    private final BindingStore store;
    private final Supplier<String> currentUid;
    private String uid;
    private String houseId;
    private long generation;
    private long syncSequence;

    ReminderSession(ReminderEngine engine, BindingStore store, Supplier<String> currentUid) {
        this.engine = engine;
        this.store = store;
        this.currentUid = currentUid;
        uid = store.uid();
        houseId = store.houseId();
        // Unbound ledgers from older versions are never adopted by the next signed-in account.
        if (uid == null || houseId == null || houseId.isEmpty() || !uid.equals(currentUid.get())) {
            end();
        }
    }

    synchronized Token start(String newHouseId) {
        String newUid = currentUid.get();
        if (newUid == null || newHouseId == null || newHouseId.isEmpty()) {
            end();
            return null;
        }
        generation++;
        syncSequence++;
        if (!newUid.equals(uid) || !newHouseId.equals(houseId)) {
            engine.clear();
        }
        uid = newUid;
        houseId = newHouseId;
        // Clear the old ledger before persisting its new owner.
        store.save(uid, houseId);
        return capture();
    }

    synchronized void end() {
        generation++;
        syncSequence++;
        uid = null;
        houseId = null;
        engine.clear();
        store.save(null, null);
    }

    synchronized Token capture() {
        return uid != null && uid.equals(currentUid.get()) ? new Token(uid, houseId, generation) : null;
    }

    synchronized boolean owns(Token token) {
        return token != null && token.generation == generation && Objects.equals(token.uid, uid)
                && Objects.equals(token.houseId, houseId);
    }

    synchronized boolean isCurrent(Token token) {
        return owns(token) && token.uid.equals(currentUid.get());
    }

    synchronized void invalidateSync() {
        syncSequence++;
    }

    synchronized boolean settingsChanged(Token token, String time, int advanceDays, Clock clock) {
        if (!isCurrent(token) || !ReminderPlanner.isValidSettings(time, advanceDays)) {
            return false;
        }
        syncSequence++;
        engine.settingsChanged(ReminderPlanner.parseTime(time), advanceDays, clock);
        return true;
    }

    synchronized Sync beginSync() {
        Token token = capture();
        return token == null ? null : new Sync(token, ++syncSequence);
    }

    synchronized void apply(Sync sync, String sourceHouseId, List<PlannedReminder> plan) {
        if (sync == null || sync.sequence != syncSequence || !isCurrent(sync.session)) {
            return;
        }
        if (!sync.session.houseId.equals(sourceHouseId)) {
            // The server says the user moved. The old house must stop while UI restores a session.
            end();
            return;
        }
        engine.apply(plan);
    }

    synchronized boolean onChanges(Token token, List<ReminderChange> changes) {
        return isCurrent(token) && engine.onChanges(token.houseId, changes);
    }

    synchronized Pending pending(ReminderKey key) {
        clearIfAccountChanged();
        Token token = capture();
        PlannedReminder reminder = token == null || !token.houseId.equals(key.houseId) ? null : engine.pending(key);
        return reminder == null ? null : new Pending(token, reminder);
    }

    synchronized boolean onDecision(Pending pending, ReminderDecision decision) {
        return isCurrent(pending.session) && engine.onDecision(pending.reminder, decision);
    }

    synchronized void clear(Token token) {
        // Cleanup must still work after Auth changed; generation prevents clearing a newer owner.
        if (owns(token)) {
            end();
        }
    }

    synchronized void clearIfAccountChanged() {
        if (!Objects.equals(uid, currentUid.get())) {
            end();
        }
    }

    synchronized void rearm() {
        clearIfAccountChanged();
        if (capture() != null) {
            engine.rearm();
        }
    }
}
