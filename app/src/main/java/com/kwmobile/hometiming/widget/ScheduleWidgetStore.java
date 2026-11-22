package com.kwmobile.hometiming.widget;

import android.content.Context;
import android.content.SharedPreferences;
import com.kwmobile.hometiming.domain.ReminderKey;
import com.kwmobile.hometiming.domain.WidgetSchedule.Entry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Private, account/house-bound read-only projection; never a second source of schedule rules. */
final class ScheduleWidgetStore {
    private final SharedPreferences prefs;
    ScheduleWidgetStore(Context context) { prefs = context.getSharedPreferences("schedule_widget", Context.MODE_PRIVATE); }
    String uid() { return prefs.getString("uid", ""); }
    String house() { return prefs.getString("house", ""); }
    boolean loaded() { return prefs.contains("rows"); }
    boolean cached() { return prefs.getBoolean("cached", true); }
    long updated() { return prefs.getLong("updated", 0); }
    void bind(String uid, String house) {
        if (!uid.equals(uid()) || !house.equals(house())) {
            prefs.edit().clear().putString("uid", uid).putString("house", house).apply();
        }
    }
    void clear() { prefs.edit().clear().apply(); }
    void save(List<Entry> rows, boolean cached) {
        JSONArray array = new JSONArray();
        try {
            for (Entry row : rows) array.put(new JSONObject().put("kind", row.kind.name()).put("id", row.id)
                    .put("name", row.name).put("due", row.due.toString()).put("category", row.category));
        } catch (JSONException error) { throw new IllegalStateException(error); }
        SharedPreferences.Editor edit = prefs.edit().putString("rows", array.toString()).putBoolean("cached", cached);
        if (!cached) edit.putLong("updated", System.currentTimeMillis());
        edit.apply();
    }
    List<Entry> rows() {
        try {
            JSONArray array = new JSONArray(prefs.getString("rows", "[]"));
            List<Entry> rows = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                rows.add(new Entry(ReminderKey.Kind.valueOf(row.getString("kind")), row.getString("id"),
                        row.getString("name"), LocalDate.parse(row.getString("due")), row.optString("category", "")));
            }
            return rows;
        } catch (JSONException | IllegalArgumentException | DateTimeException error) {
            prefs.edit().remove("rows").remove("updated").remove("cached").apply();
            return Collections.emptyList();
        }
    }
}
