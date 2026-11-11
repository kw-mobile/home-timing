package com.kwmobile.hometiming.data.firestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.Chore;
import com.kwmobile.hometiming.domain.ChoreLog;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class ChoreDocumentsTest {
    private static final Object CREATED = new Object();
    private static final Object NOW = new Object();
    private static final LocalDate SAT = LocalDate.of(2026, 11, 7);

    // The key lists firestore.rules allows.
    private static final Set<String> REQUIRED_KEYS = new HashSet<>(Arrays.asList(
            "name", "type", "weatherSensitive", "weekday", "plannedDueDate", "nextDueDate",
            "occurrenceId", "revision", "createdBy", "updatedBy", "createdAt", "updatedAt"));

    private static Chore chore(String dismissed, LocalDate lastDone, String memo) {
        return new Chore("wash", "침구 세탁", "BEDDING_WASH", true, 6, SAT, SAT.plusDays(1), "occ-1", 2,
                dismissed, lastDone, "", memo, "alice");
    }

    @Test
    public void choreFieldsMatchTheRules() {
        Map<String, Object> fields = ChoreDocuments.choreFields(chore(null, null, ""), "bob", CREATED, NOW);

        assertEquals(REQUIRED_KEYS, fields.keySet());
        assertEquals("2026-11-07", fields.get("plannedDueDate"));
        assertEquals("2026-11-08", fields.get("nextDueDate"));
        assertEquals(6, fields.get("weekday"));
        assertEquals(true, fields.get("weatherSensitive"));
        assertEquals("bob", fields.get("updatedBy"));
        assertSame(CREATED, fields.get("createdAt"));
    }

    @Test
    public void optionalValuesAreWrittenOnlyWhenSet() {
        Map<String, Object> fields = ChoreDocuments.choreFields(chore("occ-1", SAT, "서랍"), "alice", CREATED, NOW);

        assertEquals("occ-1", fields.get("dismissedOccurrenceId"));
        assertEquals("2026-11-07", fields.get("lastDoneDate"));
        assertEquals("서랍", fields.get("memo"));
        assertFalse(fields.containsKey("location"));
    }

    @Test
    public void choreRoundTripsThroughStoredValues() {
        Map<String, Object> stored = new HashMap<>(ChoreDocuments.choreFields(chore("occ-1", SAT, ""), "a", CREATED, NOW));
        stored.put("weekday", 6L);
        stored.put("revision", 2L);

        Chore read = ChoreDocuments.toChore("wash", stored);

        assertEquals(6, read.weekday);
        assertEquals(SAT.plusDays(1), read.nextDueDate);
        assertEquals(SAT, read.lastDoneDate);
        assertTrue(read.isSuggestionDismissed());
    }

    @Test
    public void malformedChoreIsSkipped() {
        Map<String, Object> stored = new HashMap<>(ChoreDocuments.choreFields(chore(null, null, ""), "a", CREATED, NOW));
        stored.remove("weatherSensitive");
        assertNull(ChoreDocuments.toChore("wash", stored));

        stored = new HashMap<>(ChoreDocuments.choreFields(chore(null, null, ""), "a", CREATED, NOW));
        stored.put("plannedDueDate", "2026-1-5");
        assertNull(ChoreDocuments.toChore("wash", stored));
    }

    @Test
    public void logIdsAreTiedToTheOccurrence() {
        assertEquals("done_occ-1", ChoreDocuments.doneLogId("occ-1"));
        assertEquals("moved_occ-1_3", ChoreDocuments.movedLogId("occ-1", 3));
    }

    @Test
    public void doneLogMatchesTheRulesAndRoundTrips() {
        Map<String, Object> fields = ChoreDocuments.doneLogFields("occ-1", SAT, "bob", "밥", NOW);

        assertEquals(new HashSet<>(Arrays.asList("action", "occurrenceId", "date", "actorId", "actorName", "createdAt")),
                fields.keySet());
        ChoreLog log = ChoreDocuments.toLog(fields);
        assertEquals("DONE", log.action);
        assertEquals(SAT, log.date);
        assertNull(log.fromDate);
    }

    @Test
    public void movedLogMatchesTheRulesAndRoundTrips() {
        Map<String, Object> fields = ChoreDocuments.movedLogFields("occ-1", SAT, SAT.plusDays(1),
                Schema.Logs.REASON_RAIN_FORECAST, "bob", "밥", NOW);

        assertEquals(new HashSet<>(Arrays.asList("action", "occurrenceId", "date", "fromDate", "reason", "actorId",
                "actorName", "createdAt")), fields.keySet());
        ChoreLog log = ChoreDocuments.toLog(fields);
        assertEquals("RESCHEDULED", log.action);
        assertEquals(SAT, log.fromDate);
        assertEquals("RAIN_FORECAST", log.reason);
    }
}
