package com.kwmobile.hometiming.domain;

/** What to do with a reminder that is about to fire (docs/MVP.md 날씨·알림 계약). */
public enum ReminderDecision {
    /** The server confirms this occurrence and schedule. */
    FIRE,
    /** Completed, deleted, or the user is no longer in the house or signed in. */
    CANCEL,
    /** Same occurrence, but its schedule changed: schedule again from fresh data. */
    RESCHEDULE,
    /** The server could not be asked in time: notify from the stored schedule. */
    UNCONFIRMED
}
