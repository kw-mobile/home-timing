package com.kwmobile.hometiming.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.domain.WidgetSchedule;
import com.kwmobile.hometiming.domain.WidgetSchedule.Entry;
import com.kwmobile.hometiming.notify.ReminderManager;
import com.kwmobile.hometiming.ui.main.MainActivity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ScheduleWidgetProvider extends AppWidgetProvider {
    public static final String EXTRA_HOUSE = "com.kwmobile.hometiming.extra.WIDGET_HOUSE";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) { refresh(context); }
    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        ScheduleWidgetManager.get(context).render();
    }
    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) refresh(context);
    }
    private static void refresh(Context context) {
        ScheduleWidgetManager manager = ScheduleWidgetManager.get(context);
        manager.render(); manager.requestSync();
    }
    static boolean hasWidgets(Context context) { return ids(context).length > 0; }
    private static int[] ids(Context context) {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, ScheduleWidgetProvider.class));
    }
    static void render(Context context, ScheduleWidgetStore store) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : ids(context)) {
            RemoteViews content;
            if (Build.VERSION.SDK_INT >= 31) {
                // Let the host choose its actual size, including orientation changes, without a wakeup.
                Map<SizeF, RemoteViews> sizes = new LinkedHashMap<>();
                sizes.put(new SizeF(180, 112), views(context, store, 180, 112));
                for (int height : new int[] {160, 208, 256, 304, 352, 448})
                    sizes.put(new SizeF(240, height), views(context, store, 240, height));
                content = new RemoteViews(sizes);
            } else {
                Bundle options = manager.getAppWidgetOptions(id);
                int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
                int maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth);
                int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 208);
                int maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight);
                content = new RemoteViews(views(context, store, maxWidth, minHeight), views(context, store, minWidth, maxHeight));
            }
            manager.updateAppWidget(id, content);
        }
    }
    static RemoteViews views(Context context, ScheduleWidgetStore store, int width, int height) {
        boolean compact = width < 240 || height < 160;
        RemoteViews view = new RemoteViews(context.getPackageName(), compact
                ? R.layout.widget_schedule_compact : R.layout.widget_schedule);
        view.setOnClickPendingIntent(android.R.id.background, open(context, store.house(), null));
        LocalDate today = WidgetSchedule.today(Clock.systemUTC());
        List<Entry> rows = WidgetSchedule.upcoming(store.rows(), today);
        boolean signedIn = !store.uid().isEmpty();
        boolean loaded = signedIn && store.loaded();
        boolean hasRows = loaded && !rows.isEmpty();
        String updated = loaded ? store.cached() || store.updated() == 0 ? context.getString(R.string.widget_cached)
                : context.getString(R.string.widget_updated, Instant.ofEpochMilli(store.updated()).atZone(ZoneId.of("Asia/Seoul"))
                        .format(DateTimeFormatter.ofPattern("M.d HH:mm"))) : "";
        view.setViewVisibility(R.id.widget_message, hasRows ? View.GONE : View.VISIBLE);
        view.setTextViewText(R.id.widget_message, context.getString(!signedIn ? R.string.widget_sign_in
                : !loaded ? R.string.widget_loading : R.string.widget_empty));
        if (compact) {
            view.setViewVisibility(R.id.widget_focus, hasRows ? View.VISIBLE : View.GONE);
            if (hasRows) {
                Entry entry = rows.get(0);
                view.setTextViewText(R.id.widget_focus_heading, context.getString(store.cached()
                        ? R.string.widget_cached : R.string.widget_focus_heading));
                view.setTextViewText(R.id.widget_focus_name, entry.name);
                view.setImageViewResource(R.id.widget_focus_icon, ScheduleWidgetIcons.of(entry));
                view.setTextViewText(R.id.widget_focus_status, WidgetSchedule.dDay(entry, today));
                view.setTextColor(R.id.widget_focus_status, statusColor(context, entry, today));
                view.setContentDescription(R.id.widget_focus, description(context, entry, today, updated));
                view.setOnClickPendingIntent(R.id.widget_focus, open(context, store.house(), entry));
            }
        } else {
            view.setTextViewText(R.id.widget_updated, updated);
            view.setTextViewText(R.id.widget_count, loaded ? context.getString(R.string.widget_count, rows.size()) : "");
            view.setViewVisibility(R.id.widget_rows, hasRows ? View.VISIBLE : View.GONE);
            view.removeAllViews(R.id.widget_rows);
            if (hasRows) {
                int slots = Math.max(2, Math.min(8, (height - 64) / 48));
                int shown = rows.size() > slots ? slots - 1 : rows.size();
                for (int i = 0; i < shown; i++) {
                    Entry entry = rows.get(i);
                    RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_schedule_row);
                    row.setTextViewText(R.id.widget_row_name, entry.name);
                    row.setImageViewResource(R.id.widget_row_icon, ScheduleWidgetIcons.of(entry));
                    row.setTextViewText(R.id.widget_row_status, WidgetSchedule.dDay(entry, today));
                    row.setTextColor(R.id.widget_row_status, statusColor(context, entry, today));
                    row.setContentDescription(R.id.widget_row, description(context, entry, today, updated));
                    row.setOnClickPendingIntent(R.id.widget_row, open(context, store.house(), entry));
                    view.addView(R.id.widget_rows, row);
                }
                if (rows.size() > shown) {
                    RemoteViews more = new RemoteViews(context.getPackageName(), R.layout.widget_schedule_text);
                    more.setTextViewText(R.id.widget_row_text, context.getString(R.string.widget_more, rows.size() - shown));
                    more.setOnClickPendingIntent(R.id.widget_row_text, open(context, store.house(), null));
                    view.addView(R.id.widget_rows, more);
                }
            }
        }
        return view;
    }
    private static int statusColor(Context context, Entry entry, LocalDate today) {
        return context.getColor(WidgetSchedule.daysUntil(entry, today) <= 0 ? R.color.ht_error : R.color.ht_primary);
    }
    private static String description(Context context, Entry entry, LocalDate today, String updated) {
        long days = WidgetSchedule.daysUntil(entry, today);
        String remaining = context.getString(days < 0 ? R.string.widget_overdue
                : days == 0 ? R.string.widget_due_today : R.string.widget_remaining, Math.abs(days));
        return context.getString(R.string.widget_item_description, entry.name, remaining, updated);
    }
    private static PendingIntent open(Context context, String house, Entry entry) {
        Uri.Builder data = new Uri.Builder().scheme("hometiming").authority("widget").appendPath(house);
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (entry != null) {
            data.appendPath(entry.kind.name()).appendPath(entry.id);
            intent.putExtra(ReminderManager.EXTRA_KIND, entry.kind.name()).putExtra(ReminderManager.EXTRA_DOCUMENT_ID, entry.id)
                    .putExtra(EXTRA_HOUSE, house);
        }
        intent.setData(data.build());
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
