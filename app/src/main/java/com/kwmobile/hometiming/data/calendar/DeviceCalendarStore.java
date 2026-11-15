package com.kwmobile.hometiming.data.calendar;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;
import androidx.core.content.ContextCompat;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.domain.CalendarEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Only touches exact installation-owned markers within explicitly selected calendars. No personal-event import. */
public final class DeviceCalendarStore {
    public static final class Choice {
        public final long id;
        public final String label;
        Choice(long id, String label) { this.id = id; this.label = label; }
    }
    private final Context context;
    private final ContentResolver resolver;
    public DeviceCalendarStore(Context context) {
        this.context = context.getApplicationContext(); resolver = context.getContentResolver();
    }
    public boolean permitted() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }
    public List<Choice> choices() {
        List<Choice> result = new ArrayList<>();
        try (Cursor c = resolver.query(CalendarContract.Calendars.CONTENT_URI,
                new String[]{"_id", "calendar_displayName", "account_name", "account_type"},
                "calendar_access_level >= ? AND deleted = 0", new String[]{"500"}, "calendar_displayName")) {
            if (c != null) while (c.moveToNext()) result.add(new Choice(c.getLong(0), c.getString(1) + " · " + c.getString(2)
                    + (CalendarContract.ACCOUNT_TYPE_LOCAL.equals(c.getString(3)) ? " · " + context.getString(R.string.calendar_local) : "")));
        }
        return result;
    }
    public void reconcile(long calendarId, String marker, List<CalendarEntry> entries) {
        Map<String, List<Long>> existing = new HashMap<>();
        try (Cursor c = resolver.query(CalendarContract.Events.CONTENT_URI, new String[]{"_id", "description"},
                "calendar_id = ? AND description LIKE ? AND deleted = 0", new String[]{Long.toString(calendarId), marker + "%"}, null)) {
            if (c == null) throw new IllegalStateException("Calendar provider unavailable");
            while (c.moveToNext()) existing.computeIfAbsent(c.getString(1), ignored -> new ArrayList<>()).add(c.getLong(0));
        }
        Set<String> desired = new HashSet<>();
        for (CalendarEntry entry : entries) {
            String description = marker + entry.key;
            desired.add(description);
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
            values.put(CalendarContract.Events.TITLE, context.getString(entry.completed ? R.string.calendar_done_title : R.string.calendar_due_title, entry.title));
            values.put(CalendarContract.Events.DESCRIPTION, description);
            values.put(CalendarContract.Events.DTSTART, entry.startMillis());
            values.put(CalendarContract.Events.DTEND, entry.endMillis());
            values.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
            values.put(CalendarContract.Events.ALL_DAY, 1);
            values.put(CalendarContract.Events.HAS_ALARM, 0);
            List<Long> matches = existing.get(description);
            if (matches == null || matches.isEmpty()) {
                if (resolver.insert(CalendarContract.Events.CONTENT_URI, values) == null) throw new IllegalStateException("Calendar insert failed");
            } else {
                resolver.update(CalendarContract.Events.CONTENT_URI, values,
                        "_id = ? AND calendar_id = ? AND description = ?", new String[]{Long.toString(matches.get(0)), Long.toString(calendarId), description});
                // Calendar edits never feed back into Firebase. Remove reminders only on our exported event.
                resolver.delete(CalendarContract.Reminders.CONTENT_URI, "event_id = ?", new String[]{Long.toString(matches.get(0))});
                for (int i = 1; i < matches.size(); i++) delete(calendarId, matches.get(i), description);
            }
        }
        for (Map.Entry<String, List<Long>> row : existing.entrySet()) if (!desired.contains(row.getKey()))
            for (long id : row.getValue()) delete(calendarId, id, row.getKey());
    }
    private void delete(long calendarId, long id, String description) {
        resolver.delete(CalendarContract.Events.CONTENT_URI, "_id = ? AND calendar_id = ? AND description = ?",
                new String[]{Long.toString(id), Long.toString(calendarId), description});
    }
}
