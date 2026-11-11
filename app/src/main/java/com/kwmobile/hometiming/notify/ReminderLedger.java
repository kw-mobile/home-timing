package com.kwmobile.hometiming.notify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kwmobile.hometiming.domain.DateRules;
import com.kwmobile.hometiming.domain.PlannedReminder;
import com.kwmobile.hometiming.domain.ReminderKey;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What this device has scheduled and already shown. Alarms do not survive a reboot and carry no
 * data of their own, so this is what the receivers read. Corrupt data reads as empty.
 */
final class ReminderLedger {
    interface Store {
        String get(String key);

        void put(String key, String value);
    }

    private static final String SCHEDULED = "scheduled";
    private static final String FIRED = "fired";

    private final Store store;
    private final Map<String, PlannedReminder> scheduled = new LinkedHashMap<>();
    /** Key → the due date it was shown for, so a moved occurrence can remind again. */
    private final Map<String, String> fired = new LinkedHashMap<>();

    ReminderLedger(Store store) {
        this.store = store;
        load();
    }

    PlannedReminder scheduled(ReminderKey key) {
        return scheduled.get(key.value());
    }

    Collection<PlannedReminder> allScheduled() {
        return Collections.unmodifiableCollection(new java.util.ArrayList<>(scheduled.values()));
    }

    Collection<String> allFired() {
        return Collections.unmodifiableCollection(new java.util.ArrayList<>(fired.keySet()));
    }

    boolean wasFired(PlannedReminder reminder) {
        return DateRules.format(reminder.dueDate).equals(fired.get(reminder.key.value()));
    }

    void schedule(PlannedReminder reminder) {
        scheduled.put(reminder.key.value(), reminder);
        save();
    }

    void unschedule(ReminderKey key) {
        scheduled.remove(key.value());
        save();
    }

    void markFired(PlannedReminder reminder) {
        scheduled.remove(reminder.key.value());
        fired.put(reminder.key.value(), DateRules.format(reminder.dueDate));
        save();
    }

    void forgetFired(String key) {
        fired.remove(key);
        save();
    }

    void clear() {
        scheduled.clear();
        fired.clear();
        save();
    }

    private void save() {
        JsonObject scheduledJson = new JsonObject();
        for (PlannedReminder reminder : scheduled.values()) {
            JsonObject object = new JsonObject();
            object.addProperty("revision", reminder.revision);
            object.addProperty("fireAt", reminder.fireAtMillis);
            object.addProperty("due", DateRules.format(reminder.dueDate));
            object.addProperty("name", reminder.name);
            object.addProperty("daysBefore", reminder.daysBefore);
            scheduledJson.add(reminder.key.value(), object);
        }
        JsonObject firedJson = new JsonObject();
        for (Map.Entry<String, String> entry : fired.entrySet()) {
            firedJson.addProperty(entry.getKey(), entry.getValue());
        }
        store.put(SCHEDULED, scheduledJson.toString());
        store.put(FIRED, firedJson.toString());
    }

    private void load() {
        try {
            String scheduledText = store.get(SCHEDULED);
            if (scheduledText != null) {
                for (Map.Entry<String, JsonElement> entry
                        : JsonParser.parseString(scheduledText).getAsJsonObject().entrySet()) {
                    ReminderKey key = ReminderKey.parse(entry.getKey());
                    if (key != null) {
                        JsonObject object = entry.getValue().getAsJsonObject();
                        scheduled.put(entry.getKey(), new PlannedReminder(key, object.get("revision").getAsInt(),
                                object.get("fireAt").getAsLong(), DateRules.parse(object.get("due").getAsString()),
                                object.get("name").getAsString(), object.get("daysBefore").getAsInt()));
                    }
                }
            }
            String firedText = store.get(FIRED);
            if (firedText != null) {
                for (Map.Entry<String, JsonElement> entry
                        : JsonParser.parseString(firedText).getAsJsonObject().entrySet()) {
                    fired.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        } catch (RuntimeException e) {
            scheduled.clear();
            fired.clear();
        }
    }
}
