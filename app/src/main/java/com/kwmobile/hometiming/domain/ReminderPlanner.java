package com.kwmobile.hometiming.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * When each reminder fires (docs/MVP.md F-06), in Asia/Seoul at the personal reminder time. A due
 * reminder whose time has passed fires now, once; a passed advance reminder is skipped, because the
 * due reminder still follows it.
 */
public final class ReminderPlanner {
    public static final int ADVANCE_DAYS_MAX = 30;

    // STRICT: the default resolver turns 24:00 into 00:00, which firestore.rules refuses.
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private ReminderPlanner() {
    }

    public static List<PlannedReminder> plan(String houseId, Collection<Item> items, Collection<Chore> chores,
                                             LocalTime notifyTime, int advanceDays, Clock clock) {
        long now = clock.millis();
        List<PlannedReminder> plan = new ArrayList<>();
        for (Item item : items) {
            add(plan, ReminderKey.Kind.ITEM, houseId, item.id, item.occurrenceId, item.revision, item.nextDueDate,
                    item.name, notifyTime, advanceDays, now);
        }
        for (Chore chore : chores) {
            add(plan, ReminderKey.Kind.CHORE, houseId, chore.id, chore.occurrenceId, chore.revision, chore.nextDueDate,
                    chore.name, notifyTime, advanceDays, now);
        }
        return Collections.unmodifiableList(plan);
    }

    private static void add(List<PlannedReminder> plan, ReminderKey.Kind kind, String houseId, String id,
                            String occurrenceId, int revision, LocalDate due, String name, LocalTime time,
                            int advanceDays, long now) {
        long dueAt = millis(due, time);
        ReminderKey dueKey = new ReminderKey(kind, houseId, id, ReminderKey.Type.DUE, occurrenceId);
        plan.add(new PlannedReminder(dueKey, revision, Math.max(dueAt, now), due, name, 0));
        if (advanceDays > 0) {
            long advanceAt = millis(due.minusDays(advanceDays), time);
            if (advanceAt > now) {
                ReminderKey key = new ReminderKey(kind, houseId, id, ReminderKey.Type.ADVANCE, occurrenceId);
                plan.add(new PlannedReminder(key, revision, advanceAt, due, name, advanceDays));
            }
        }
    }

    private static long millis(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(DateRules.ZONE).toInstant().toEpochMilli();
    }

    /** {@code HH:mm} as stored in the user document; null when malformed. */
    public static LocalTime parseTime(String text) {
        try {
            return text == null || text.length() != 5 ? null : LocalTime.parse(text, TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static boolean isValidSettings(String notifyTime, int advanceDays) {
        return parseTime(notifyTime) != null && advanceDays >= 0 && advanceDays <= ADVANCE_DAYS_MAX;
    }
}
