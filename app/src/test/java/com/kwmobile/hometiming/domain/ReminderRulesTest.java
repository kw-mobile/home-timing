package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.ReminderKey.Kind;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ReminderRulesTest {
    private static ScheduleState state(String id, String occurrence, int revision) {
        return new ScheduleState(Kind.ITEM, id, occurrence, revision);
    }

    @Test
    public void firesWhenOccurrenceAndRevisionMatch() {
        assertEquals(ReminderDecision.FIRE, ReminderRules.check(state("a", "occ-1", 2), "occ-1", 2));
    }

    @Test
    public void cancelsACompletedOccurrence() {
        assertEquals(ReminderDecision.CANCEL, ReminderRules.check(state("a", "occ-2", 3), "occ-1", 2));
    }

    @Test
    public void cancelsADeletedDocument() {
        assertEquals(ReminderDecision.CANCEL, ReminderRules.check(null, "occ-1", 2));
    }

    @Test
    public void reschedulesWhenTheScheduleChanged() {
        assertEquals(ReminderDecision.RESCHEDULE, ReminderRules.check(state("a", "occ-1", 3), "occ-1", 2));
    }

    @Test
    public void theFirstListIsOnlyABaseline() {
        assertTrue(new ReminderRules.Tracker().update(Arrays.asList(state("a", "occ-1", 1))).isEmpty());
    }

    @Test
    public void noChangeReportsNothing() {
        ReminderRules.Tracker tracker = new ReminderRules.Tracker();
        tracker.update(Arrays.asList(state("a", "occ-1", 1)));

        assertTrue(tracker.update(Arrays.asList(state("a", "occ-1", 1))).isEmpty());
    }

    @Test
    public void anotherDevicesCompletionCancelsTheOldOccurrence() {
        ReminderRules.Tracker tracker = new ReminderRules.Tracker();
        tracker.update(Arrays.asList(state("a", "occ-1", 1)));

        List<ReminderChange> changes = tracker.update(Arrays.asList(state("a", "occ-2", 2)));

        assertEquals(1, changes.size());
        ReminderChange change = changes.get(0);
        assertEquals(ReminderChange.Type.COMPLETED, change.type);
        assertEquals(Kind.ITEM, change.kind);
        assertEquals("a", change.documentId);
        assertEquals("occ-1", change.previousOccurrenceId);
        assertEquals("occ-2", change.occurrenceId);
    }

    @Test
    public void aRevisionChangeIsReschedule() {
        ReminderRules.Tracker tracker = new ReminderRules.Tracker();
        tracker.update(Arrays.asList(state("a", "occ-1", 1)));

        ReminderChange change = tracker.update(Arrays.asList(state("a", "occ-1", 2))).get(0);

        assertEquals(ReminderChange.Type.RESCHEDULED, change.type);
        assertEquals("occ-1", change.previousOccurrenceId);
    }

    @Test
    public void reportsAdditionsAndDeletions() {
        ReminderRules.Tracker tracker = new ReminderRules.Tracker();
        tracker.update(Arrays.asList(state("a", "occ-1", 1)));

        List<ReminderChange> added = tracker.update(Arrays.asList(state("a", "occ-1", 1), state("b", "occ-9", 1)));
        List<ReminderChange> deleted = tracker.update(Collections.singletonList(state("b", "occ-9", 1)));

        assertEquals(ReminderChange.Type.ADDED, added.get(0).type);
        assertNull(added.get(0).previousOccurrenceId);
        assertEquals("occ-9", added.get(0).occurrenceId);
        assertEquals(ReminderChange.Type.DELETED, deleted.get(0).type);
        assertEquals("occ-1", deleted.get(0).previousOccurrenceId);
        assertNull(deleted.get(0).occurrenceId);
    }

    @Test
    public void statesComeFromItemsAndChores() {
        Chore chore = new Chore("wash", "침구", "ETC", true, 6, java.time.LocalDate.of(2026, 11, 7),
                java.time.LocalDate.of(2026, 11, 7), "occ-1", 4, null, null, "", "", "alice");

        ScheduleState state = ScheduleState.of(chore);

        assertEquals(Kind.CHORE, state.kind);
        assertEquals("wash", state.documentId);
        assertEquals("occ-1", state.occurrenceId);
        assertEquals(4, state.revision);
    }
}
