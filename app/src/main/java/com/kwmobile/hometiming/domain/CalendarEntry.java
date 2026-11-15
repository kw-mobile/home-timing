package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** A projection, never a second source of schedule truth. All-day dates use UTC per CalendarContract. */
public final class CalendarEntry {
    public final String key, title;
    public final LocalDate date;
    public final boolean completed;
    public CalendarEntry(String key, String title, LocalDate date, boolean completed) {
        this.key = key; this.title = title; this.date = date; this.completed = completed;
    }
    public long startMillis() { return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(); }
    public long endMillis() { return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(); }
    /** Current occurrence and latest actual completion only; no invented future recurrence/history. */
    public static List<CalendarEntry> project(List<Item> items, List<Chore> chores) {
        List<CalendarEntry> result = new ArrayList<>();
        for (Item item : items) {
            result.add(new CalendarEntry("item:" + item.id + ":next", item.name, item.nextDueDate, false));
            if (item.hasCompleted) result.add(new CalendarEntry("item:" + item.id + ":done", item.name, item.lastReplacedDate, true));
        }
        for (Chore chore : chores) {
            result.add(new CalendarEntry("chore:" + chore.id + ":next", chore.name, chore.nextDueDate, false));
            if (chore.lastDoneDate != null) result.add(new CalendarEntry("chore:" + chore.id + ":done", chore.name, chore.lastDoneDate, true));
        }
        return result;
    }
}
