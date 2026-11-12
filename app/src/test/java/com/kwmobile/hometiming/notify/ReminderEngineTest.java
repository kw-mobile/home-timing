package com.kwmobile.hometiming.notify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.PlannedReminder;
import com.kwmobile.hometiming.domain.ReminderChange;
import com.kwmobile.hometiming.domain.ReminderDecision;
import com.kwmobile.hometiming.domain.ReminderKey;
import com.kwmobile.hometiming.domain.ReminderKey.Kind;
import com.kwmobile.hometiming.domain.ReminderKey.Type;
import com.kwmobile.hometiming.domain.ReminderRules;
import com.kwmobile.hometiming.domain.ScheduleState;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

public class ReminderEngineTest {
    private static final LocalDate DUE = LocalDate.of(2026, 11, 14);

    private final Map<String, String> stored = new HashMap<>();
    private final List<String> set = new ArrayList<>();
    private final List<String> cancelled = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private final List<String> dismissed = new ArrayList<>();

    private ReminderEngine engine;

    private ReminderLedger.Store store() {
        return new ReminderLedger.Store() {
            @Override
            public String get(String key) {
                return stored.get(key);
            }

            @Override
            public void put(String key, String value) {
                stored.put(key, value);
            }
        };
    }

    private ReminderEngine newEngine() {
        return new ReminderEngine(new ReminderLedger(store()), new ReminderEngine.Alarms() {
            @Override
            public void set(PlannedReminder reminder) {
                set.add(reminder.key.value());
            }

            @Override
            public void cancel(ReminderKey key) {
                cancelled.add(key.value());
            }
        }, new ReminderEngine.Display() {
            @Override
            public void show(PlannedReminder reminder) {
                shown.add(reminder.key.value());
            }

            @Override
            public void dismiss(String key) {
                dismissed.add(key);
            }
        });
    }

    @Before
    public void setUp() {
        engine = newEngine();
    }

    private static PlannedReminder reminder(String id, Type type, String occurrence, long fireAt, LocalDate due) {
        return new PlannedReminder(new ReminderKey(Kind.ITEM, "house-a", id, type, occurrence), 1, fireAt, due,
                "필터", type == Type.ADVANCE ? 3 : 0);
    }

    private static PlannedReminder due(String id, String occurrence, long fireAt) {
        return reminder(id, Type.DUE, occurrence, fireAt, DUE);
    }

    private static void clear(List<?>... lists) {
        for (List<?> list : lists) {
            list.clear();
        }
    }

    @Test
    public void schedulesAPlanOnceAndOnlyResetsChangedTimes() {
        PlannedReminder a = due("a", "occ-1", 100);
        engine.apply(Collections.singletonList(a));
        assertEquals(Collections.singletonList(a.key.value()), set);

        clear(set);
        engine.apply(Collections.singletonList(a));
        assertTrue(set.isEmpty());

        engine.apply(Collections.singletonList(due("a", "occ-1", 200)));
        assertEquals(Collections.singletonList(a.key.value()), set);
    }

    @Test
    public void cancelsWhatThePlanNoLongerHas() {
        PlannedReminder a = due("a", "occ-1", 100);
        engine.apply(Collections.singletonList(a));

        engine.apply(Collections.<PlannedReminder>emptyList());

        assertEquals(Collections.singletonList(a.key.value()), cancelled);
        assertNull(engine.pending(a.key));
    }

    @Test
    public void aShownReminderIsNotRepeatedByTheNextSync() {
        PlannedReminder overdue = due("a", "occ-1", 100);
        engine.apply(Collections.singletonList(overdue));
        engine.onDecision(overdue, ReminderDecision.FIRE);
        clear(set);

        // The next sync plans the same overdue reminder "now" again.
        engine.apply(Collections.singletonList(due("a", "occ-1", 500)));

        assertEquals(Collections.singletonList(overdue.key.value()), shown);
        assertTrue(set.isEmpty());
        assertNull(engine.pending(overdue.key));
    }

    @Test
    public void aMovedDateRemindsAgain() {
        PlannedReminder first = due("a", "occ-1", 100);
        engine.apply(Collections.singletonList(first));
        engine.onDecision(first, ReminderDecision.FIRE);
        clear(set);

        engine.apply(Collections.singletonList(reminder("a", Type.DUE, "occ-1", 900, DUE.plusDays(1))));

        assertEquals(Collections.singletonList(first.key.value()), set);
    }

    @Test
    public void unconfirmedFiresFromTheStoredSchedule() {
        PlannedReminder a = due("a", "occ-1", 100);
        engine.apply(Collections.singletonList(a));

        assertFalse(engine.onDecision(a, ReminderDecision.UNCONFIRMED));

        assertEquals(Collections.singletonList(a.key.value()), shown);
    }

    @Test
    public void cancelDropsWithoutShowing() {
        PlannedReminder a = due("a", "occ-1", 100);
        engine.apply(Collections.singletonList(a));

        assertFalse(engine.onDecision(a, ReminderDecision.CANCEL));

        assertTrue(shown.isEmpty());
        assertNull(engine.pending(a.key));
    }

    @Test
    public void rescheduleAsksForASync() {
        PlannedReminder a = due("a", "occ-1", 100);
        engine.apply(Collections.singletonList(a));

        assertTrue(engine.onDecision(a, ReminderDecision.RESCHEDULE));

        assertTrue(shown.isEmpty());
        assertNull(engine.pending(a.key));
    }

    @Test
    public void aDecisionForAnAlreadyHandledReminderDoesNothing() {
        PlannedReminder a = due("a", "occ-1", 100);
        engine.apply(Collections.singletonList(a));
        engine.onDecision(a, ReminderDecision.FIRE);

        assertFalse(engine.onDecision(a, ReminderDecision.FIRE));

        assertEquals(1, shown.size());
    }

    @Test
    public void anotherDevicesCompletionCancelsAlarmsAndShownReminders() {
        PlannedReminder dueOld = due("a", "occ-1", 100);
        PlannedReminder advanceOld = reminder("a", Type.ADVANCE, "occ-1", 50, DUE);
        engine.apply(Arrays.asList(dueOld, advanceOld));
        engine.onDecision(advanceOld, ReminderDecision.FIRE);
        ReminderRules.Tracker tracker = new ReminderRules.Tracker();
        tracker.update(Collections.singletonList(new ScheduleState(Kind.ITEM, "a", "occ-1", 1)));
        List<ReminderChange> changes =
                tracker.update(Collections.singletonList(new ScheduleState(Kind.ITEM, "a", "occ-2", 2)));

        assertTrue(engine.onChanges("house-a", changes));

        assertTrue(cancelled.contains(dueOld.key.value()));
        assertTrue(dismissed.contains(advanceOld.key.value()));
        assertNull(engine.pending(dueOld.key));
    }

    @Test
    public void aDeletionCancelsWithoutASync() {
        PlannedReminder a = due("a", "occ-1", 100);
        engine.apply(Collections.singletonList(a));
        ReminderRules.Tracker tracker = new ReminderRules.Tracker();
        tracker.update(Collections.singletonList(new ScheduleState(Kind.ITEM, "a", "occ-1", 1)));

        assertFalse(engine.onChanges("house-a", tracker.update(Collections.<ScheduleState>emptyList())));

        assertTrue(cancelled.contains(a.key.value()));
    }

    @Test
    public void aShownAdvanceReminderStaysWhileItsOccurrenceRuns() {
        PlannedReminder advance = reminder("a", Type.ADVANCE, "occ-1", 50, DUE);
        PlannedReminder dueOne = due("a", "occ-1", 100);
        engine.apply(Arrays.asList(advance, dueOne));
        engine.onDecision(advance, ReminderDecision.FIRE);

        // Its time passed, so the next plan has only the due reminder.
        engine.apply(Collections.singletonList(dueOne));
        assertTrue(dismissed.isEmpty());

        // Completed elsewhere and synced: the occurrence left the plan.
        engine.apply(Collections.singletonList(due("a", "occ-2", 900)));
        assertTrue(dismissed.contains(advance.key.value()));
    }

    @Test
    public void rearmSetsEveryStoredAlarmAgain() {
        engine.apply(Arrays.asList(due("a", "occ-1", 100), due("b", "occ-9", 200)));
        clear(set);

        newEngine().rearm();

        assertEquals(2, set.size());
    }

    @Test
    public void signOutRemovesEverything() {
        PlannedReminder a = due("a", "occ-1", 100);
        PlannedReminder b = due("b", "occ-9", 200);
        engine.apply(Arrays.asList(a, b));
        engine.onDecision(b, ReminderDecision.FIRE);

        engine.clear();

        assertTrue(cancelled.contains(a.key.value()));
        assertTrue(dismissed.containsAll(Arrays.asList(a.key.value(), b.key.value())));
        assertNull(newEngine().pending(a.key));
    }

    @Test
    public void theLedgerSurvivesARestart() {
        PlannedReminder a = due("a", "occ-1", 100);
        engine.apply(Collections.singletonList(a));

        PlannedReminder restored = newEngine().pending(a.key);

        assertNotNull(restored);
        assertEquals(100, restored.fireAtMillis);
        assertEquals(DUE, restored.dueDate);
        assertEquals("필터", restored.name);
    }

    @Test
    public void aCorruptLedgerReadsAsEmpty() {
        stored.put("scheduled", "not json");

        newEngine().rearm();

        assertTrue(set.isEmpty());
    }

    @Test
    public void changingTheTimeDuringAServerCheckKeepsTheNewReservation() {
        PlannedReminder nine = due("a", "occ-1", 9_000);
        PlannedReminder ten = due("a", "occ-1", 10_000);
        engine.apply(Collections.singletonList(nine));
        engine.apply(Collections.singletonList(ten));

        for (ReminderDecision oldAnswer : ReminderDecision.values()) {
            assertFalse(engine.onDecision(nine, oldAnswer));
            assertEquals(10_000, engine.pending(ten.key).fireAtMillis);
        }
        assertTrue(shown.isEmpty());
        engine.apply(Collections.singletonList(ten));
        assertNotNull(engine.pending(ten.key));
        engine.onDecision(ten, ReminderDecision.FIRE);
        assertEquals(1, shown.size());
    }

    private ReminderSession session(AtomicReference<String> uid, ReminderEngine target) {
        return new ReminderSession(target, new ReminderSession.BindingStore() {
            @Override
            public String uid() { return stored.get("owner_uid"); }

            @Override
            public String houseId() { return stored.get("owner_house"); }

            @Override
            public void save(String owner, String houseId) {
                stored.put("owner_uid", owner);
                stored.put("owner_house", houseId);
            }
        }, uid::get);
    }

    @Test
    public void signOutThenSignInAndHouseChangeDiscardEveryOldResponse() {
        AtomicReference<String> uid = new AtomicReference<>("alice");
        ReminderSession session = session(uid, engine);
        ReminderSession.Token oldListener = session.start("house-a");
        PlannedReminder a = due("a", "occ-1", 100);
        session.apply(session.beginSync(), "house-a", Collections.singletonList(a));
        ReminderSession.Pending oldCheck = session.pending(a.key);
        ReminderSession.Sync oldWorker = session.beginSync();

        session.end();
        session.start("house-a"); // Even the same account and house is a new session.
        session.apply(session.beginSync(), "house-a", Collections.singletonList(a));
        session.apply(oldWorker, "house-a", Collections.emptyList());
        assertFalse(session.onDecision(oldCheck, ReminderDecision.FIRE));
        ReminderRules.Tracker tracker = new ReminderRules.Tracker();
        tracker.update(Collections.singletonList(new ScheduleState(Kind.ITEM, "a", "occ-1", 1)));
        session.onChanges(oldListener, tracker.update(Collections.emptyList()));
        assertNotNull(session.pending(a.key));

        ReminderSession.Sync beforeMove = session.beginSync();
        session.start("house-b");
        PlannedReminder b = new PlannedReminder(new ReminderKey(Kind.ITEM, "house-b", "b", Type.DUE, "occ-9"),
                1, 200, DUE, "침구", 0);
        session.apply(session.beginSync(), "house-b", Collections.singletonList(b));
        session.apply(beforeMove, "house-a", Collections.singletonList(a));
        assertNull(session.pending(a.key));
        assertNotNull(session.pending(b.key));

        ReminderSession.Sync aliceWork = session.beginSync();
        uid.set("bob"); // Auth can change before screen callbacks reach the manager.
        session.clear(aliceWork.session);
        assertNull(session.pending(b.key));
        session.start("house-b");
        session.apply(session.beginSync(), "house-b", Collections.singletonList(b));
        session.apply(aliceWork, "house-b", Collections.emptyList());
        assertNotNull(session.pending(b.key));
        assertTrue(shown.isEmpty());
    }

    @Test
    public void settingsChangeInvalidatesAnOldSyncAndRestartKeepsOnlyItsOwner() {
        AtomicReference<String> uid = new AtomicReference<>("alice");
        ReminderSession session = session(uid, engine);
        session.start("house-a");
        ReminderSession.Sync oldSettings = session.beginSync();
        PlannedReminder nine = due("a", "occ-1", 9_000);
        PlannedReminder ten = due("a", "occ-1", 10_000);
        session.invalidateSync();
        session.apply(oldSettings, "house-a", Collections.singletonList(nine));
        assertNull(session.pending(nine.key));
        session.apply(session.beginSync(), "house-a", Collections.singletonList(ten));

        clear(set);
        session(uid, newEngine()).rearm();
        assertEquals(1, set.size());
        uid.set("bob");
        clear(set);
        ReminderSession otherAccount = session(uid, newEngine());
        otherAccount.rearm();
        assertTrue(set.isEmpty());
        assertNull(otherAccount.pending(ten.key));
    }

}
