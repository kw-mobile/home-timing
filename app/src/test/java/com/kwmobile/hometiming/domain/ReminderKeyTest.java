package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.kwmobile.hometiming.domain.ReminderKey.Kind;
import com.kwmobile.hometiming.domain.ReminderKey.Type;

import org.junit.Test;

public class ReminderKeyTest {
    private static ReminderKey key(Type type, String occurrence) {
        return new ReminderKey(Kind.ITEM, "house-a", "filter", type, occurrence);
    }

    @Test
    public void carriesKindHouseDocumentTypeAndOccurrence() {
        assertEquals("ITEM/house-a/filter/DUE/occ-1", key(Type.DUE, "occ-1").value());
    }

    @Test
    public void roundTrips() {
        ReminderKey original = new ReminderKey(Kind.CHORE, "house-a", "wash", Type.ADVANCE, "occ-1");

        ReminderKey parsed = ReminderKey.parse(original.value());

        assertEquals(original, parsed);
        assertEquals(Kind.CHORE, parsed.kind);
        assertEquals("house-a", parsed.houseId);
        assertEquals("wash", parsed.documentId);
        assertEquals(Type.ADVANCE, parsed.type);
        assertEquals("occ-1", parsed.occurrenceId);
    }

    @Test
    public void anOccurrenceWithASlashSurvives() {
        assertEquals("a/b", ReminderKey.parse(key(Type.DUE, "a/b").value()).occurrenceId);
    }

    @Test
    public void eachOccurrenceAndTypeIsADifferentReminder() {
        assertNotEquals(key(Type.DUE, "occ-1"), key(Type.DUE, "occ-2"));
        assertNotEquals(key(Type.DUE, "occ-1"), key(Type.ADVANCE, "occ-1"));
        assertEquals(key(Type.DUE, "occ-1").hashCode(), key(Type.DUE, "occ-1").hashCode());
    }

    @Test
    public void refusesIdsThatWouldBreakTheFormat() {
        assertThrows(IllegalArgumentException.class, () -> new ReminderKey(Kind.ITEM, "a/b", "x", Type.DUE, "o"));
        assertThrows(IllegalArgumentException.class, () -> new ReminderKey(Kind.ITEM, "a", "", Type.DUE, "o"));
        assertThrows(IllegalArgumentException.class, () -> new ReminderKey(Kind.ITEM, "a", "x", Type.DUE, ""));
    }

    @Test
    public void textThatIsNotAKeyParsesToNull() {
        assertNull(ReminderKey.parse(null));
        assertNull(ReminderKey.parse("ITEM/house/filter/DUE"));
        assertNull(ReminderKey.parse("GARDEN/house/filter/DUE/occ"));
        assertNull(ReminderKey.parse("ITEM/house/filter/LATER/occ"));
        assertNull(ReminderKey.parse("ITEM//filter/DUE/occ"));
    }
}
