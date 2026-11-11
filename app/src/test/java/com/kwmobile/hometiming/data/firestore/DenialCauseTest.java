package com.kwmobile.hometiming.data.firestore;

import static org.junit.Assert.assertEquals;

import com.kwmobile.hometiming.domain.ItemSchedule.Conflict;

import org.junit.Test;

/** A refused change is reported by what changed on the server, not as "no permission" (#55). */
public class DenialCauseTest {
    @Test
    public void anotherDevicesCompletionReadsAsAlreadyCompleted() {
        assertEquals(ItemError.ALREADY_COMPLETED, ItemRepository.denialCause(true, Conflict.ALREADY_COMPLETED));
    }

    @Test
    public void aScheduleChangeReadsAsScheduleChanged() {
        assertEquals(ItemError.SCHEDULE_CHANGED, ItemRepository.denialCause(true, Conflict.SCHEDULE_CHANGED));
    }

    @Test
    public void aDeletedDocumentReadsAsNotFound() {
        assertEquals(ItemError.NOT_FOUND, ItemRepository.denialCause(false, null));
    }

    @Test
    public void anUnchangedDocumentStaysARealRefusal() {
        assertEquals(ItemError.PERMISSION_DENIED, ItemRepository.denialCause(true, Conflict.NONE));
        assertEquals(ItemError.PERMISSION_DENIED, ItemRepository.denialCause(true, null));
    }
}
