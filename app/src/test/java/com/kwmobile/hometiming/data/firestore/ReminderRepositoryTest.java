package com.kwmobile.hometiming.data.firestore;

import static org.junit.Assert.assertEquals;

import com.kwmobile.hometiming.domain.ReminderDecision;

import org.junit.Test;

public class ReminderRepositoryTest {
    @Test
    public void refusalMeansTheReminderNoLongerBelongsToThisUser() {
        assertEquals(ReminderDecision.CANCEL, ReminderRepository.decisionForFailure(ItemError.PERMISSION_DENIED));
        assertEquals(ReminderDecision.CANCEL, ReminderRepository.decisionForFailure(ItemError.NOT_SIGNED_IN));
    }

    @Test
    public void otherFailuresFireFromTheStoredSchedule() {
        assertEquals(ReminderDecision.UNCONFIRMED, ReminderRepository.decisionForFailure(ItemError.NETWORK));
        assertEquals(ReminderDecision.UNCONFIRMED, ReminderRepository.decisionForFailure(ItemError.UNKNOWN));
    }

    @Test
    public void aTimeoutFiresFromTheStoredSchedule() {
        ItemError error = ItemTasks.classify(new java.util.concurrent.TimeoutException());

        assertEquals(ReminderDecision.UNCONFIRMED, ReminderRepository.decisionForFailure(error));
    }
}
