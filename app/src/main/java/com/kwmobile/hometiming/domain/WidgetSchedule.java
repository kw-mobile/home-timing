package com.kwmobile.hometiming.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Unfinished occurrences through seven days ahead, ordered by actual execution date. */
public final class WidgetSchedule {
    private WidgetSchedule() { }
    public static LocalDate today(Clock clock) {
        return DateRules.today(clock);
    }
    public static final class Entry {
        public final ReminderKey.Kind kind;
        public final String id, name, category;
        public final LocalDate due;
        public Entry(ReminderKey.Kind kind, String id, String name, LocalDate due) {
            this(kind, id, name, due, "");
        }
        public Entry(ReminderKey.Kind kind, String id, String name, LocalDate due, String category) {
            this.kind = kind; this.id = id; this.name = name; this.due = due; this.category = category;
        }
    }
    public static List<Entry> project(List<Item> items, List<Chore> chores) {
        List<Entry> rows = new ArrayList<>();
        for (Item item : items) rows.add(new Entry(ReminderKey.Kind.ITEM, item.id, item.name, item.nextDueDate, item.category));
        for (Chore chore : chores) rows.add(new Entry(ReminderKey.Kind.CHORE, chore.id, chore.name, chore.nextDueDate, chore.type));
        return rows;
    }
    public static long daysUntil(Entry entry, LocalDate today) {
        return ReplacementCalculator.daysUntil(entry.due, today);
    }
    public static String dDay(Entry entry, LocalDate today) {
        long days = daysUntil(entry, today);
        return days == 0 ? "D-day" : days < 0 ? "D+" + (-days) : "D-" + days;
    }
    public static List<Entry> upcoming(List<Entry> entries, LocalDate today) {
        List<Entry> rows = new ArrayList<>();
        for (Entry row : entries) if (!row.due.isAfter(today.plusDays(7))) rows.add(row);
        rows.sort(Comparator.comparing((Entry row) -> row.due).thenComparing(row -> row.name)
                .thenComparing(row -> row.kind).thenComparing(row -> row.id));
        return rows;
    }
}
