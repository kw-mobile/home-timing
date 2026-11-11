package com.kwmobile.hometiming.data.firestore;

import com.kwmobile.hometiming.domain.Chore;
import com.kwmobile.hometiming.domain.Item;

import java.util.List;

/** Everything needed to rebuild reminders, read from the server rather than the cache. */
public final class ReminderSnapshot {
    public final String houseId;
    /** Personal reminder time, {@code HH:mm}. */
    public final String notifyTime;
    /** Days before the due date for the advance reminder; 0 means off. */
    public final int advanceDays;
    public final List<Item> items;
    public final List<Chore> chores;

    ReminderSnapshot(String houseId, String notifyTime, int advanceDays, List<Item> items, List<Chore> chores) {
        this.houseId = houseId;
        this.notifyTime = notifyTime;
        this.advanceDays = advanceDays;
        this.items = items;
        this.chores = chores;
    }
}
