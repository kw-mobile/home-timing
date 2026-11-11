package com.kwmobile.hometiming.notify;

import static org.junit.Assert.assertEquals;

import com.kwmobile.hometiming.data.firestore.ItemError;
import com.kwmobile.hometiming.notify.ReminderResyncWorker.Outcome;

import org.junit.Test;

public class ReminderResyncWorkerTest {
    @Test
    public void signedOutClearsReminders() {
        assertEquals(Outcome.CLEAR, ReminderResyncWorker.outcomeOf(ItemError.NOT_SIGNED_IN));
    }

    @Test
    public void networkProblemsRetry() {
        assertEquals(Outcome.RETRY, ReminderResyncWorker.outcomeOf(ItemError.NETWORK));
        assertEquals(Outcome.RETRY, ReminderResyncWorker.outcomeOf(ItemError.UNKNOWN));
    }

    @Test
    public void failuresRetryingCannotFixKeepTheSchedule() {
        assertEquals(Outcome.STOP, ReminderResyncWorker.outcomeOf(ItemError.NOT_CONFIGURED));
        assertEquals(Outcome.STOP, ReminderResyncWorker.outcomeOf(ItemError.PERMISSION_DENIED));
        assertEquals(Outcome.STOP, ReminderResyncWorker.outcomeOf(ItemError.NOT_FOUND));
    }
}
