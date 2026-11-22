package com.kwmobile.hometiming.widget;

import android.app.Activity;
import android.app.Instrumentation;
import android.appwidget.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.gms.tasks.*;
import com.google.firebase.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.auth.*;
import com.kwmobile.hometiming.data.firestore.*;
import com.kwmobile.hometiming.domain.*;
import com.kwmobile.hometiming.ui.main.*;
import java.io.FileOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Dedicated debug AVD + demo Firebase only. Actual repository, host RemoteViews, and tap routes. */
public final class ScheduleWidgetInstrumentation extends Instrumentation {
    private final StringBuilder log = new StringBuilder();
    private MainActivity activity;
    private AppWidgetHost host;
    private int widgetId;
    private String authPort, firestorePort;
    @Override public void onCreate(Bundle args) {
        authPort = args.getString("authPort", "19106"); firestorePort = args.getString("firestorePort", "18106");
        super.onCreate(args); start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check(BuildConfig.DEBUG && (Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish")), "dedicated debug emulator");
            check(BuildConfig.FIREBASE_CONFIGURED, "configured build");
            runOnMainSync(() -> {
                for (FirebaseApp app : FirebaseApp.getApps(getTargetContext())) app.delete();
                FirebaseApp.initializeApp(getTargetContext(), new FirebaseOptions.Builder().setProjectId("demo-home-timing")
                        .setApplicationId("1:123456789:android:abcdef123456").setApiKey("demo-emulator-api-key").build());
                FirebaseAuth.getInstance().useEmulator("10.0.2.2", Integer.parseInt(authPort));
                FirebaseAuth.getInstance().signOut();
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
                db.useEmulator("10.0.2.2", Integer.parseInt(firestorePort));
            });
            String email = "widget-" + System.currentTimeMillis() + "@example.test";
            Session session = await(main(() -> AuthRepository.getInstance().signUp("위젯 검증", email, "Demo-only-12345")));
            LocalDate today = WidgetSchedule.today(Clock.systemUTC());
            ItemRepository items = ItemRepository.getInstance();
            ChoreRepository chores = ChoreRepository.getInstance();
            Item overdue = await(main(() -> items.create(session.houseId, new ItemDraft("칫솔", "욕실", today.minusDays(93), 90, "", "", ""))));
            Item filter = await(main(() -> items.create(session.houseId, new ItemDraft("정수기 필터", "주방", today.minusDays(58), 60, "", "", ""))));
            Item boundary = await(main(() -> items.create(session.houseId, new ItemDraft("공기청정기 필터", "기타", today.minusDays(53), 60, "", "", ""))));
            activity = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ScheduleWidgetManager manager = ScheduleWidgetManager.get(getTargetContext());
            waitFor(() -> manager.store.loaded() && manager.store.rows().size() == 3, "foreground projection populated");
            check(WidgetSchedule.upcoming(manager.store.rows(), today).get(0).id.equals(overdue.id), "overdue first");
            // A v0.5.0 projection has no category; it must remain readable during upgrade/offline.
            getTargetContext().getSharedPreferences("schedule_widget", Context.MODE_PRIVATE).edit()
                    .putString("rows", "[{\"kind\":\"ITEM\",\"id\":\"legacy\",\"name\":\"칫솔\",\"due\":\"" + today + "\"}]").commit();
            check(manager.store.rows().get(0).category.isEmpty() && manager.store.loaded(), "v0.5.0 cache without category remains readable");
            check(ScheduleWidgetIcons.of(manager.store.rows().get(0)) == R.drawable.ic_brush, "legacy cache has meaningful icon fallback");
            manager.sync(); waitFor(() -> manager.store.rows().size() == 3, "server restores complete projection");
            host = main(() -> new AppWidgetHost(getTargetContext(), 108));
            widgetId = main(() -> host.allocateAppWidgetId());
            getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
            boolean bound = AppWidgetManager.getInstance(getTargetContext()).bindAppWidgetIdIfAllowed(widgetId,
                    new ComponentName(getTargetContext(), ScheduleWidgetProvider.class));
            getUiAutomation().dropShellPermissionIdentity();
            check(bound, "actual app widget host binding");
            showHost(208); snapshot("light");
            check(main(() -> text(activity.findViewById(android.R.id.content)).contains("칫솔")), "host inflated actual schedule rows");
            check(main(() -> text(activity.findViewById(android.R.id.content)).contains("D+3")), "D+3 overdue status");
            check(main(() -> text(activity.findViewById(android.R.id.content)).contains("D-2")), "D-2 future status");
            check(main(() -> text(activity.findViewById(android.R.id.content)).contains("D-7")), "seventh day included");
            check(main(() -> ((ImageView)byId(activity.findViewById(android.R.id.content), R.id.widget_row_icon)).getDrawable() != null), "host vector icon rendered");
            check(main(() -> findText(activity.findViewById(android.R.id.content), "칫솔").getContentDescription().toString().contains("3일 지남")), "accessible overdue meaning");
            renderPreview(Configuration.UI_MODE_NIGHT_YES, 208, 336, "dark");
            renderPreview(Configuration.UI_MODE_NIGHT_NO, 160, 240, "narrow");
            check(main(() -> text(activity.findViewById(android.R.id.content)).contains("외 2개")), "short list retains first item and overflow");
            showHost(112, 180); snapshot("compact");
            check(main(() -> text(activity.findViewById(android.R.id.content)).contains("D+3")
                    && text(activity.findViewById(android.R.id.content)).contains("칫솔")), "actual host switches to compact focus card");
            runOnMainSync(() -> byId(activity.findViewById(android.R.id.content), R.id.widget_focus).performClick());
            waitFor(() -> new ViewModelProvider(activity).get(DesignPreviewViewModel.class).state.route.id.equals("item:" + overdue.id), "compact card opens first item detail");
            Item due = await(main(() -> items.create(session.houseId, new ItemDraft("수세미", "주방", today.minusDays(30), 30, "", "", ""))));
            Item excluded = await(main(() -> items.create(session.houseId, new ItemDraft("8일 뒤 필터", "욕실", today.minusDays(52), 60, "", "", ""))));
            Chore tomorrow = await(main(() -> chores.create(session.houseId, new ChoreDraft("침구 세탁", "BEDDING_WASH", true,
                    today.plusDays(1).getDayOfWeek().getValue(), today, "", ""))));
            waitFor(() -> manager.store.rows().size() == 6, "all actual items and chores persisted");
            showHost(352); snapshot("expanded");
            check(main(() -> text(activity.findViewById(android.R.id.content)).contains("D-day")), "due today status");
            check(main(() -> !text(activity.findViewById(android.R.id.content)).contains("8일 뒤 필터")), "eighth day excluded");
            runOnMainSync(() -> findText(activity.findViewById(android.R.id.content), "침구 세탁").performClick());
            waitFor(() -> new ViewModelProvider(activity).get(DesignPreviewViewModel.class).state.route.id.equals("chore:" + tomorrow.id), "list tap opens matching chore detail");
            main(() -> { new ViewModelProvider(activity).get(MvpViewModel.class).openReminder("ITEM", overdue.id, "other-house"); return null; });
            check(main(() -> new ViewModelProvider(activity).get(DesignPreviewViewModel.class).state.route.id.equals("chore:" + tomorrow.id)), "stale house tap rejected");
            await(main(() -> items.complete(session.houseId, due, today)));
            waitFor(() -> WidgetSchedule.upcoming(manager.store.rows(), today).size() == 4, "completion removes old occurrence from seven-day list");
            await(main(() -> items.delete(session.houseId, overdue.id)));
            await(main(() -> items.delete(session.houseId, filter.id)));
            await(main(() -> items.delete(session.houseId, boundary.id)));
            await(main(() -> chores.delete(session.houseId, tomorrow.id)));
            waitFor(() -> WidgetSchedule.upcoming(manager.store.rows(), today).isEmpty(), "deletion updates list");
            renderPreview(Configuration.UI_MODE_NIGHT_NO, 208, 336, "empty");
            check(main(() -> text(activity.findViewById(android.R.id.content)).contains("다가오는 7일 일정이 없어요")), "empty state");
            await(FirebaseFirestore.getInstance().disableNetwork());
            try {
                await(ReminderRepository.getInstance().readFromServer(session.uid, session.houseId));
                throw new AssertionError("offline server read must fail");
            } catch (java.util.concurrent.ExecutionException expected) { check(true, "offline server read fails"); }
            manager.sync();
            check(manager.store.loaded() && !manager.store.rows().isEmpty(), "offline keeps stored next occurrence");
            await(FirebaseFirestore.getInstance().enableNetwork());
            check(manager.sync(), "server sync recovers after offline");
            // Corrupt local projection must show loading, never an authoritative empty schedule.
            getTargetContext().getSharedPreferences("schedule_widget", Context.MODE_PRIVATE).edit()
                    .putString("rows", "[{\"kind\":\"ITEM\",\"id\":\"bad\",\"name\":\"bad\",\"due\":\"invalid-date\"}]").commit();
            check(manager.store.rows().isEmpty() && !manager.store.loaded(), "invalid cached date invalidates projection");
            check(manager.sync(), "server restores invalid projection");
            main(() -> { new ViewModelProvider(activity).get(MvpViewModel.class).signOut(); return null; });
            waitFor(() -> manager.store.uid().isEmpty(), "sign out clears projection");
            check(!manager.store.loaded(), "no old rows after sign out");
            renderPreview(Configuration.UI_MODE_NIGHT_NO, 112, 180, "signed-out");
            check(main(() -> text(activity.findViewById(android.R.id.content)).contains("로그인해 주세요")), "signed out state");
            result.putString("stream", log + "Widget PASS (demo Firebase, Android emulator; not physical device)\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            android.util.Log.e("WidgetTest", "Failed", error);
            result.putString("stream", log + "Widget FAIL: " + error + "\n"); finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (host != null) runOnMainSync(() -> { host.stopListening(); if (widgetId != 0) host.deleteAppWidgetId(widgetId); });
        }
    }
    private void showHost(int height) throws Exception { showHost(height, 336); }
    private void showHost(int height, int width) throws Exception {
        runOnMainSync(() -> {
            // Host the widget in a stable surface; normal app/weather renders must not replace it.
            new ViewModelProvider(activity).get(MvpViewModel.class).changes().removeObservers(activity);
            new ViewModelProvider(activity).get(WeatherViewModel.class).state().removeObservers(activity);
            com.kwmobile.hometiming.data.update.UpdateManager.get(activity).changes().removeObservers(activity);
            Bundle options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width);
            AppWidgetManager.getInstance(activity).updateAppWidgetOptions(widgetId, options);
            host.startListening();
            AppWidgetHostView view = host.createView(getTargetContext(), widgetId, AppWidgetManager.getInstance(activity).getAppWidgetInfo(widgetId));
            view.setPadding(0, 0, 0, 0);
            FrameLayout root = new FrameLayout(activity); root.setBackgroundColor(0xffeeeeee);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(width), dp(height), Gravity.CENTER);
            root.addView(view, params); activity.setContentView(root);
            ScheduleWidgetManager.get(activity).render();
        });
        waitForIdleSync(); SystemClock.sleep(600);
    }
    private void renderPreview(int night, int height, int width, String name) throws Exception {
        runOnMainSync(() -> {
            Configuration config = new Configuration(activity.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
            Context themed = getTargetContext().createConfigurationContext(config);
            View view = ScheduleWidgetProvider.views(themed, ScheduleWidgetManager.get(activity).store, width, height).apply(themed, null);
            FrameLayout root = new FrameLayout(activity); root.setBackgroundColor(night == Configuration.UI_MODE_NIGHT_YES ? 0xff080808 : 0xffeeeeee);
            root.addView(view, new FrameLayout.LayoutParams(dp(width), dp(height), Gravity.CENTER)); activity.setContentView(root);
        });
        waitForIdleSync(); snapshot(name);
    }
    private void snapshot(String name) throws Exception {
        SystemClock.sleep(250);
        try (FileOutputStream out = new FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null), "widget-" + name + ".png"))) {
            getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        Bitmap card = main(() -> {
            ViewGroup content = activity.findViewById(android.R.id.content);
            View target = ((ViewGroup)content.getChildAt(0)).getChildAt(0);
            Bitmap bitmap = Bitmap.createBitmap(target.getWidth(), target.getHeight(), Bitmap.Config.ARGB_8888);
            target.draw(new android.graphics.Canvas(bitmap));
            return bitmap;
        });
        try (FileOutputStream out = new FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null), "widget-" + name + "-card.png"))) {
            card.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        card.recycle();
    }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private void check(boolean success, String description) { if (!success) throw new AssertionError(description); log.append("PASS ").append(description).append('\n'); }
    private <T> T main(Supplier<T> action) {
        AtomicReference<T> value = new AtomicReference<>(); AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> { try { value.set(action.get()); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError("Main-thread probe failed", failure.get());
        return value.get();
    }
    private <T> T await(Task<T> task) throws Exception { return Tasks.await(task, 40, TimeUnit.SECONDS); }
    private void waitFor(Supplier<Boolean> condition, String description) throws Exception {
        long end = SystemClock.elapsedRealtime() + 15000;
        while (SystemClock.elapsedRealtime() < end) { if (main(condition)) { check(true, description); return; } SystemClock.sleep(100); }
        throw new AssertionError(description);
    }
    private String text(View view) {
        StringBuilder all = new StringBuilder(); if (view instanceof TextView) all.append(((TextView)view).getText());
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) all.append(text(((ViewGroup)view).getChildAt(i)));
        return all.toString();
    }
    private View byId(View view, int id) {
        if (view.getId() == id) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
            View found = byId(((ViewGroup)view).getChildAt(i), id); if (found != null) return found;
        }
        return null;
    }
    private View findText(View view, String text) {
        if (view instanceof TextView && text.equals(((TextView)view).getText().toString())) return (View)view.getParent();
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
            View found = findText(((ViewGroup)view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
}
