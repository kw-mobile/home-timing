package com.kwmobile.hometiming.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Today's unfinished occurrences (including overdue) and tomorrow, using actual execution dates. */
public final class WidgetSchedule {
    private WidgetSchedule() { }
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
    }
    public static final class Entry {
        public final ReminderKey.Kind kind;
        public final String id, name;
        public final LocalDate due;
        public Entry(ReminderKey.Kind kind, String id, String name, LocalDate due) {
            this.kind = kind; this.id = id; this.name = name; this.due = due;
        }
    }
    public static List<Entry> project(List<Item> items, List<Chore> chores) {
        List<Entry> rows = new ArrayList<>();
        for (Item item : items) rows.add(new Entry(ReminderKey.Kind.ITEM, item.id, item.name, item.nextDueDate));
        for (Chore chore : chores) rows.add(new Entry(ReminderKey.Kind.CHORE, chore.id, chore.name, chore.nextDueDate));
        return rows;
    }
    public static List<Entry> day(List<Entry> entries, LocalDate today, boolean tomorrow) {
        List<Entry> rows = new ArrayList<>();
        for (Entry row : entries) {
            if (tomorrow ? row.due.equals(today.plusDays(1)) : !row.due.isAfter(today)) rows.add(row);
        }
        rows.sort(Comparator.comparing((Entry row) -> row.due).thenComparing(row -> row.name)
                .thenComparing(row -> row.kind).thenComparing(row -> row.id));
        return rows;
    }
}
