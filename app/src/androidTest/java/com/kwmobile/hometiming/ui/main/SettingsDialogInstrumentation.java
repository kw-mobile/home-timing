package com.kwmobile.hometiming.ui.main;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.provider.CalendarContract;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.auth.Session;
import com.kwmobile.hometiming.domain.House;
import com.kwmobile.hometiming.ui.calendar.CalendarSettingsDialogFragment;
import com.kwmobile.hometiming.ui.calendar.CalendarSettingsViewModel;
import com.kwmobile.hometiming.ui.weather.WeatherSettingsDialogFragment;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.util.function.BooleanSupplier;

/** Own empty AVD only. No server account/writes; creates and removes one explicitly owned local calendar. */
public final class SettingsDialogInstrumentation extends Instrumentation {
    private volatile MainActivity activity;
    private Bundle args;
    private Uri ownCalendar;
    private boolean oldTestMode;
    private final StringBuilder log = new StringBuilder();
    private final Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks() {
        public void onActivityResumed(Activity value) { if (value instanceof MainActivity) activity = (MainActivity)value; }
        public void onActivityCreated(Activity a, Bundle b) {} public void onActivityStarted(Activity a) {}
        public void onActivityPaused(Activity a) {} public void onActivityStopped(Activity a) {}
        public void onActivitySaveInstanceState(Activity a, Bundle b) {} public void onActivityDestroyed(Activity a) {}
    };
    @Override public void onCreate(Bundle args) { this.args = args == null ? new Bundle() : args; super.onCreate(args); start(); }
    @Override public void onStart() {
        boolean passed = false; Bundle out = new Bundle();
        Application app = (Application)getTargetContext().getApplicationContext();
        app.registerActivityLifecycleCallbacks(lifecycle);
        android.accessibilityservice.AccessibilityServiceInfo accessibility = getUiAutomation().getServiceInfo();
        accessibility.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        getUiAutomation().setServiceInfo(accessibility);
        try {
            check(com.kwmobile.hometiming.BuildConfig.DEBUG && ("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE)), "dedicated debug AVD");
            check(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null, "no existing account");
            activity = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(() -> {
                MvpViewModel live = new ViewModelProvider(activity).get(MvpViewModel.class);
                live.session = new Session("modal-probe", "modal-probe", "모달 점검", false);
                live.house = new House("modal-probe", "modal-probe", 1, 0, 0, null, null, null, null);
                for (String name : new String[]{"itemCache", "choreCache", "houseCache"}) set(live, name, false);
                new ViewModelProvider(activity).get(DesignPreviewViewModel.class).tab("settings"); invoke(activity, "render");
            }); idle();
            oldTestMode = new ViewModelProvider(activity).get(WeatherViewModel.class).isTestMode();
            runOnMainSync(() -> ((androidx.core.widget.NestedScrollView)activity.findViewById(R.id.preview_scroll)).fullScroll(View.FOCUS_DOWN)); idle();
            int settingsScroll = main(() -> ((androidx.core.widget.NestedScrollView)activity.findViewById(R.id.preview_scroll)).getScrollY());
            clickRow(getTargetContext().getString(R.string.calendar_title));
            await(() -> calendar() != null && calendar().isResumed(), "calendar opens above settings");
            runOnMainSync(() -> CalendarSettingsDialogFragment.open(activity.getSupportFragmentManager()));
            check(main(() -> activity.getSupportFragmentManager().getFragments().stream().filter(f -> f instanceof CalendarSettingsDialogFragment).count()) == 1, "repeated open makes one modal");
            check(main(() -> new ViewModelProvider(activity).get(DesignPreviewViewModel.class).state.route.screen).equals("settings"), "underlying settings route preserved");
            snap("calendar");
            runOnMainSync(() -> ((android.widget.ScrollView)calendar().requireDialog().findViewById(R.id.calendar_settings_root)).fullScroll(View.FOCUS_DOWN)); idle();
            check(main(() -> calendar().requireDialog().findViewById(R.id.calendar_choose).getGlobalVisibleRect(new android.graphics.Rect())), "calendar choice reachable by scrolling");
            check(main(() -> ((androidx.appcompat.app.AlertDialog)calendar().requireDialog()).getButton(-2).getGlobalVisibleRect(new android.graphics.Rect())), "modal close remains visible");
            runOnMainSync(() -> calendar().requireDialog().findViewById(R.id.calendar_details).performClick());
            await(() -> action() != null && action().isResumed(), "details child shown");
            recreate();
            await(() -> calendar() != null && action() != null && action().isResumed(), "parent and details restore after rotation");
            back(); await(() -> action() == null, "details back keeps parent");
            if (ContextCompat.checkSelfPermission(getTargetContext(), Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                runOnMainSync(() -> calendar().requireDialog().findViewById(R.id.calendar_choose).performClick());
                permission("permission_deny_button");
                await(() -> calendar() != null && main(() -> calendar().requireDialog().getWindow().getDecorView().hasWindowFocus()), "permission denial returns to same modal");
                check(!new ViewModelProvider(calendar()).get(CalendarSettingsViewModel.class).manager.permitted(), "denial does not enable calendar");
                snap("permission-denied");
                runOnMainSync(() -> calendar().requireDialog().findViewById(R.id.calendar_choose).performClick());
                permission("permission_allow_button");
                await(() -> new ViewModelProvider(calendar()).get(CalendarSettingsViewModel.class).manager.permitted(), "permission grant returns");
            }
            createCalendar();
            runOnMainSync(() -> new ViewModelProvider(calendar()).get(CalendarSettingsViewModel.class).refresh());
            await(() -> main(() -> !new ViewModelProvider(calendar()).get(CalendarSettingsViewModel.class).busy
                    && !new ViewModelProvider(calendar()).get(CalendarSettingsViewModel.class).choices.isEmpty()), "own local calendar choices loaded");
            runOnMainSync(() -> calendar().requireDialog().findViewById(R.id.calendar_choose).performClick());
            await(() -> action() != null && action().isResumed(), "calendar picker child shown");
            recreate(); await(() -> calendar() != null && action() != null && action().isResumed(), "picker restores after rotation");
            await(() -> main(() -> {
                android.widget.ListAdapter choices = ((androidx.appcompat.app.AlertDialog)action().requireDialog()).getListView().getAdapter();
                for (int i = 0; i < choices.getCount(); i++) if (choices.getItem(i).toString().contains("모달 검증 전용")) return true;
                return false;
            }), "picker labels restore with current choices");
            snap("picker"); back(); await(() -> action() == null, "picker canceled to parent");
            check(!new ViewModelProvider(calendar()).get(CalendarSettingsViewModel.class).manager.enabled(), "cancel does not opt in or write events");
            back(); await(() -> calendar() == null, "back closes calendar without navigation");
            check(main(() -> new ViewModelProvider(activity).get(DesignPreviewViewModel.class).state.route.screen).equals("settings"), "settings remains after close");
            check(Math.abs(main(() -> ((androidx.core.widget.NestedScrollView)activity.findViewById(R.id.preview_scroll)).getScrollY()) - settingsScroll) <= 2, "settings scroll preserved through modal and rotation: " + settingsScroll + " -> " + main(() -> ((androidx.core.widget.NestedScrollView)activity.findViewById(R.id.preview_scroll)).getScrollY()));
            int weatherScroll = main(() -> ((androidx.core.widget.NestedScrollView)activity.findViewById(R.id.preview_scroll)).getScrollY());
            clickRow(getTargetContext().getString(R.string.weather_ui_mode));
            await(() -> weatherDialog() != null && weatherDialog().isResumed(), "short weather preference opens as modal");
            runOnMainSync(() -> ((SwitchMaterial)weatherDialog().requireDialog().findViewById(R.id.weather_test_toggle)).setChecked(!oldTestMode));
            check(new ViewModelProvider(activity).get(WeatherViewModel.class).isTestMode() != oldTestMode, "switch changes shared local mode");
            recreate(); await(() -> weatherDialog() != null && weatherDialog().isResumed(), "weather modal restores after rotation");
            check(main(() -> ((SwitchMaterial)weatherDialog().requireDialog().findViewById(R.id.weather_test_toggle)).isChecked()) != oldTestMode, "weather choice retained");
            snap("weather"); back(); await(() -> weatherDialog() == null, "weather back returns to settings");
            check(Math.abs(main(() -> ((androidx.core.widget.NestedScrollView)activity.findViewById(R.id.preview_scroll)).getScrollY()) - weatherScroll) <= 2, "weather keeps settings scroll after source refresh and rotation: " + weatherScroll + " -> " + main(() -> ((androidx.core.widget.NestedScrollView)activity.findViewById(R.id.preview_scroll)).getScrollY()));
            passed = true;
        } catch (Throwable error) { log.append("FAIL ").append(error).append('\n'); }
        finally {
            try { if (ownCalendar != null) { getTargetContext().getContentResolver().delete(ownCalendar, null, null);
                try (Cursor remaining = getTargetContext().getContentResolver().query(ownCalendar, new String[]{"_id"}, null, null, null)) {
                    check(remaining != null && !remaining.moveToFirst(), "own local calendar deletion read back");
                }
                log.append("CLEANUP own local calendar removed\n"); } }
            catch (RuntimeException error) { passed = false; log.append("FAIL calendar cleanup ").append(error).append('\n'); }
            if (activity != null) runOnMainSync(() -> { new ViewModelProvider(activity).get(WeatherViewModel.class).setTestMode(oldTestMode); activity.finish(); });
            app.unregisterActivityLifecycleCallbacks(lifecycle);
        }
        out.putString("stream", log + "SETTINGS MODALS " + (passed ? "PASS" : "FAIL") + ": memory household, owned local calendar; no Firebase writes\n");
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, out);
    }
    private CalendarSettingsDialogFragment calendar() { return main(() -> (CalendarSettingsDialogFragment)activity.getSupportFragmentManager().findFragmentByTag(CalendarSettingsDialogFragment.TAG)); }
    private DialogFragment action() { return main(() -> calendar() == null ? null : (DialogFragment)calendar().getChildFragmentManager().findFragmentByTag("calendar-action")); }
    private WeatherSettingsDialogFragment weatherDialog() { return main(() -> (WeatherSettingsDialogFragment)activity.getSupportFragmentManager().findFragmentByTag(WeatherSettingsDialogFragment.TAG)); }
    private void clickRow(String title) { runOnMainSync(() -> { View v = findText(activity.getWindow().getDecorView(), title); check(v != null, "settings row exists: " + title); while (!v.isClickable()) v = (View)v.getParent(); v.performClick(); }); idle(); }
    private View findText(View view, String text) { if (view instanceof TextView && ((TextView)view).getText().toString().equals(text)) return view; if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) { View found=findText(((ViewGroup)view).getChildAt(i), text); if(found!=null)return found; } return null; }
    private void recreate() { MainActivity before = activity; runOnMainSync(before::recreate); await(() -> activity != before && main(() -> activity.hasWindowFocus()) || activity != before && (calendar() != null || weatherDialog() != null), "activity recreated"); idle(); }
    private void back() { getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); idle(); }
    private void permission(String id) {
        String full = "com.android.permissioncontroller:id/" + id;
        await(() -> findId(getUiAutomation().getRootInActiveWindow(), full) != null, "OS permission prompt " + id);
        check(findId(getUiAutomation().getRootInActiveWindow(), full).performAction(AccessibilityNodeInfo.ACTION_CLICK), "OS permission action " + id);
        idle();
    }
    private AccessibilityNodeInfo findId(AccessibilityNodeInfo n,String id) { if(n==null)return null; String actual=n.getViewIdResourceName();
        if(actual!=null && actual.endsWith(id.substring(id.indexOf(":id/"))))return n;
        String text = n.getText() == null ? "" : n.getText().toString().replace('’', '\'');
        if (n.getPackageName() != null && n.getPackageName().toString().contains("permissioncontroller")
                && (id.endsWith("permission_deny_button") && text.equals("Don't allow")
                || id.endsWith("permission_allow_button") && text.equals("Allow"))) return n; for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo x=findId(n.getChild(i),id);if(x!=null)return x;}return null; }
    private void createCalendar() {
        String account="modal-qa-"+SystemClock.uptimeMillis();
        Uri sync=CalendarContract.Calendars.CONTENT_URI.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME,account).appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL).build();
        ContentValues v=new ContentValues();v.put(CalendarContract.Calendars.ACCOUNT_NAME,account);v.put(CalendarContract.Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL);
        v.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,"모달 검증 전용");v.put(CalendarContract.Calendars.OWNER_ACCOUNT,account);v.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,700);v.put(CalendarContract.Calendars.VISIBLE,1);v.put(CalendarContract.Calendars.SYNC_EVENTS,1);
        Uri inserted=getTargetContext().getContentResolver().insert(sync,v);check(inserted!=null,"created explicitly owned local calendar");
        ownCalendar=inserted.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true").appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME,account).appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL).build();
    }
    private void snap(String name) throws Exception { idle();Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream o=new FileOutputStream(new File(getTargetContext().getFilesDir(),args.getString("prefix","modal")+"-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,o);}b.recycle();log.append("CAPTURE ").append(name).append('\n'); }
    private void idle() { waitForIdleSync(); SystemClock.sleep(500); }
    private void await(BooleanSupplier test,String label) { long end=SystemClock.elapsedRealtime()+20000;while(!test.getAsBoolean() && SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);check(test.getAsBoolean(),label); }
    private void check(boolean value,String label) { if(!value)throw new AssertionError(label);log.append("PASS ").append(label).append('\n'); }
    private <T>T main(java.util.function.Supplier<T> work) { if(Looper.myLooper()==Looper.getMainLooper())return work.get();java.util.concurrent.atomic.AtomicReference<T> value=new java.util.concurrent.atomic.AtomicReference<>();runOnMainSync(()->value.set(work.get()));return value.get(); }
    private static void invoke(Object target,String name) { try{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}catch(Exception e){throw new RuntimeException(e);} }
    private static void set(Object target,String name,Object value) { try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception e){throw new RuntimeException(e);} }
}
