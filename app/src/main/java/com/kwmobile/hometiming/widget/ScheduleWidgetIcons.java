package com.kwmobile.hometiming.widget;

import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.domain.ReminderKey;
import com.kwmobile.hometiming.domain.WidgetSchedule.Entry;

/** Existing app vector family; older projections without categories retain a safe kind/name fallback. */
final class ScheduleWidgetIcons {
    private ScheduleWidgetIcons() { }
    static int of(Entry entry) {
        if (entry.kind == ReminderKey.Kind.CHORE) {
            switch (entry.category) {
                case "CLEANING": return R.drawable.ic_clean;
                case "VENTILATION": return R.drawable.ic_wind;
                case "BEDDING_WASH": case "OUTDOOR_DRY": return R.drawable.ic_wash;
                default: return R.drawable.ic_clean;
            }
        }
        if (entry.name.contains("칫솔")) return R.drawable.ic_brush;
        if (entry.name.contains("필터")) return R.drawable.ic_filter;
        return R.drawable.ic_update;
    }
}
