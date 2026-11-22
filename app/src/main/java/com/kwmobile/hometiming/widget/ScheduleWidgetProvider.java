package com.kwmobile.hometiming.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

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
        for (int id : ids(context)) manager.updateAppWidget(id, views(context, store,
                manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 336)));
    }
    static RemoteViews views(Context context, ScheduleWidgetStore store, int height) {
        RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.widget_schedule);
        LocalDate today = WidgetSchedule.today(Clock.systemUTC());
        view.setTextViewText(R.id.widget_date, today.format(DateTimeFormatter.ofPattern("M.d")));
        view.setOnClickPendingIntent(R.id.widget_header, open(context, store.house(), null));
        view.setOnClickPendingIntent(R.id.widget_message, open(context, store.house(), null));
        List<Entry> rows = store.rows();
        boolean signedIn = !store.uid().isEmpty();
        boolean loaded = signedIn && store.loaded();
        view.setViewVisibility(R.id.widget_days, loaded ? View.VISIBLE : View.GONE);
        view.setViewVisibility(R.id.widget_message, loaded ? View.GONE : View.VISIBLE);
        view.setTextViewText(R.id.widget_message, context.getString(signedIn ? R.string.widget_loading : R.string.widget_sign_in));
        String updated = "";
        if (loaded) {
            updated = store.cached() || store.updated() == 0 ? context.getString(R.string.widget_cached)
                    : context.getString(R.string.widget_updated, Instant.ofEpochMilli(store.updated()).atZone(ZoneId.of("Asia/Seoul"))
                            .format(DateTimeFormatter.ofPattern("M.d HH:mm")));
            // Reserve a 48dp overflow row when entries do not fit. Both days always remain visible.
            int slots = Math.max(2, Math.min(4, (height - 144) / 96));
            section(context, view, R.id.widget_today_rows, WidgetSchedule.day(rows, today, false), today, store.house(), slots);
            section(context, view, R.id.widget_tomorrow_rows, WidgetSchedule.day(rows, today, true), today, store.house(), slots);
        }
        view.setTextViewText(R.id.widget_updated, updated);
        return view;
    }
    private static void section(Context context, RemoteViews parent, int target, List<Entry> rows,
                                LocalDate today, String house, int slots) {
        parent.removeAllViews(target);
        if (rows.isEmpty()) { parent.addView(target, textRow(context, context.getString(R.string.widget_empty), house)); return; }
        int shown = rows.size() > slots ? slots - 1 : rows.size();
        for (int i = 0; i < shown; i++) {
            Entry entry = rows.get(i);
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_schedule_row);
            row.setTextViewText(R.id.widget_row_name, entry.name);
            long overdue = ChronoUnit.DAYS.between(entry.due, today);
            row.setViewVisibility(R.id.widget_row_status, overdue > 0 ? View.VISIBLE : View.GONE);
            row.setTextViewText(R.id.widget_row_status, overdue > 0 ? context.getString(R.string.widget_overdue, overdue) : "");
            row.setOnClickPendingIntent(R.id.widget_row, open(context, house, entry));
            parent.addView(target, row);
        }
        if (rows.size() > shown) parent.addView(target, textRow(context,
                context.getString(R.string.widget_more, rows.size() - shown), house));
    }
    private static RemoteViews textRow(Context context, String text, String house) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_schedule_text);
        row.setTextViewText(R.id.widget_row_text, text);
        row.setOnClickPendingIntent(R.id.widget_row_text, open(context, house, null));
        return row;
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
