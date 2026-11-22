package com.kwmobile.hometiming.ui.main;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.auth.Session;
import com.kwmobile.hometiming.domain.House;
import com.kwmobile.hometiming.domain.Member;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Dedicated empty debug AVD. Actual screen binders with memory data; never signs in or saves server data. */
public final class FlowUiInstrumentation extends Instrumentation {
    private volatile MainActivity activity;
    private volatile Activity resumed;
    private DesignPreviewViewModel ui;
    private MvpViewModel live;
    private String prefix;
    private boolean oldWeather;
    private final StringBuilder log = new StringBuilder();
    private final Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks() {
        public void onActivityResumed(Activity a) { resumed = a; if (a instanceof MainActivity) activity = (MainActivity)a; }
        public void onActivityCreated(Activity a, Bundle b) {} public void onActivityStarted(Activity a) {}
        public void onActivityPaused(Activity a) {} public void onActivityStopped(Activity a) {}
        public void onActivitySaveInstanceState(Activity a, Bundle b) {} public void onActivityDestroyed(Activity a) {}
    };
    @Override public void onCreate(Bundle args) { prefix = args == null ? "flow" : args.getString("prefix", "flow"); super.onCreate(args); start(); }
    @Override public void onStart() {
        boolean success = false; Bundle result = new Bundle();
        Application app = (Application)getTargetContext().getApplicationContext(); app.registerActivityLifecycleCallbacks(lifecycle);
        android.accessibilityservice.AccessibilityServiceInfo info = getUiAutomation().getServiceInfo();
        info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        getUiAutomation().setServiceInfo(info);
        try {
            check(BuildConfig.DEBUG && ("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE)), "dedicated debug emulator");
            check(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null, "no existing account");
            activity = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ui = new ViewModelProvider(activity).get(DesignPreviewViewModel.class);
            live = new ViewModelProvider(activity).get(MvpViewModel.class);
            oldWeather = new ViewModelProvider(activity).get(WeatherViewModel.class).isTestMode();
            snap("login");
            click(R.id.auth_switch); check(route().equals("signup:"), "login to signup");
            click(R.id.auth_submit);
            check(main(() -> activity.findViewById(R.id.auth_name_error).getVisibility() == View.VISIBLE), "signup empty validation inline");
            snap("signup-errors"); back(); check(route().equals("login:"), "signup back to login");
            go("reset", ""); click(R.id.auth_submit);
            check(main(() -> activity.findViewById(R.id.auth_email_error).getVisibility() == View.VISIBLE), "reset invalid email stays on form");
            snap("reset-errors"); back();
            runOnMainSync(() -> {
                live.session = new Session("flow-probe", "flow-probe", "흐름 점검", false);
                live.house = new House("flow-probe", "flow-probe", 2, 3, 2, null, null, null, "123456");
                live.members = Arrays.asList(new Member("flow-probe", "흐름 점검", true), new Member("flow-other", "가족", false));
                for (String field : new String[]{"itemCache", "choreCache", "houseCache"}) set(live, field, false);
                ui.state.store.reset(); ui.state.scenario = "normal"; ui.state.invite = "123456";
                ui.tab("home"); invoke("render");
            }); idle(); snap("home");
            go("items", ""); snap("items");
            runOnMainSync(() -> invoke("edit", new Class<?>[]{String.class}, "")); idle();
            click(R.id.edit_submit);
            check(main(() -> activity.findViewById(R.id.edit_name_error).getVisibility() == View.VISIBLE), "empty name cannot save");
            runOnMainSync(() -> ((EditText)activity.findViewById(R.id.edit_name)).setText("보존할 이름"));
            back(); check(route().startsWith("editor:"), "dirty editor awaits discard confirmation");
            dismissDialog(false); check(main(() -> ui.state.draft.name.equals("보존할 이름")), "discard cancel keeps draft");
            snap("editor-item");
            MainActivity old = activity;
            runOnMainSync(old::recreate);
            long until = SystemClock.elapsedRealtime() + 10000;
            while(activity == old && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100);
            idle();
            check(activity != old && main(() -> ui.state.draft.name.equals("보존할 이름")), "rotation retains unsaved editor draft");
            runOnMainSync(() -> { set(live, "busy", true); invoke("back"); }); idle();
            check(route().startsWith("editor:") && main(() -> ui.state.draft != null), "saving blocks back before acknowledgement");
            runOnMainSync(() -> set(live, "busy", false));
            back(); dismissDialog(true); check(route().equals("items:"), "confirmed discard returns to origin");
            runOnMainSync(() -> invoke("edit", new Class<?>[]{String.class}, "brush")); idle();
            check(main(() -> countButtons(activity.findViewById(R.id.editor_kind))) == 0, "existing item type cannot switch to chore");
            snap("edit-existing"); back();
            runOnMainSync(() -> invoke("edit", new Class<?>[]{String.class}, "")); idle();
            clickText(getTargetContext().getString(R.string.preview_copy_088));
            check(main(() -> ui.state.draft.chore), "new draft switches to chore");
            check(main(() -> activity.findViewById(R.id.editor_photo_name).getVisibility() == View.GONE), "chore has no photo action");
            snap("editor-chore"); back(); dismissDialog(true);
            go("detail", "brush"); snap("detail-item"); bottom(); snap("history");
            go("detail", "bedding"); snap("detail-chore");
            go("calendar", ""); snap("calendar");
            clickDescription(getTargetContext().getString(R.string.preview_next_month));
            snap("calendar-next");
            go("weather", ""); snap("weather-unset");
            clickText(getTargetContext().getString(R.string.ui_weather_set_region));
            check(route().equals("preferences:region"), "unset weather action leads to region setup");
            WeatherViewModel weather = new ViewModelProvider(activity).get(WeatherViewModel.class);
            runOnMainSync(() -> {
                weather.setTestMode(true);
                live.house = new House("flow-probe", "flow-probe", 2, 3, 2, "서울특별시 마포구", 59, 127, "123456");
                weather.useSharedRegion(live.house);
            });
            long end = SystemClock.elapsedRealtime() + 15000;
            while((weather.current().loading || weather.current().result == null) && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100);
            check(weather.current().result != null, "test forecast loaded for layout only");
            go("weather", ""); snap("weather-test");
            runOnMainSync(() -> ((androidx.lifecycle.MutableLiveData<WeatherViewModel.State>)get(weather,"state"))
                    .setValue(new WeatherViewModel.State(true, weather.current().result, null)));
            idle();
            check(main(() -> !text(activity.getWindow().getDecorView(), getTargetContext().getString(R.string.ui_weather_refreshing)).isEnabled()), "cached forecast refresh disables repeated action");
            snap("weather-refreshing");
            runOnMainSync(() -> ((androidx.lifecycle.MutableLiveData<WeatherViewModel.State>)get(weather,"state"))
                    .setValue(new WeatherViewModel.State(false, weather.current().result, null)));
            go("home", ""); snap("home-test");
            go("settings", ""); snap("settings"); bottom(); snap("settings-account");
            clickText(getTargetContext().getString(R.string.calendar_title)); snap("calendar-settings"); systemBack();
            check(route().equals("settings:"), "calendar modal closes to settings");
            clickText(getTargetContext().getString(R.string.weather_ui_mode)); snap("weather-settings"); systemBack();
            check(route().equals("settings:"), "weather modal closes to settings");
            clickText(getTargetContext().getString(R.string.update_title)); idle();
            check(resumed instanceof com.kwmobile.hometiming.ui.update.UpdateActivity, "settings opens update activity");
            snap("update"); systemBack(); check(resumed == activity && route().equals("settings:"), "update back returns to settings");
            bottom();
            clickText(getTargetContext().getString(R.string.mvp_delete_account));
            snap("delete-account");
            android.view.accessibility.AccessibilityNodeInfo submit = null;
            for (int i=0;i<12;i++) {
                java.util.List<android.view.accessibility.AccessibilityNodeInfo> matches=getUiAutomation().getRootInActiveWindow()
                        .findAccessibilityNodeInfosByViewId("com.kwmobile.hometiming:id/account_delete_submit");
                if(!matches.isEmpty() && matches.get(0).isVisibleToUser()){submit=matches.get(0);break;}
                java.util.List<android.view.accessibility.AccessibilityNodeInfo> scroll=getUiAutomation().getRootInActiveWindow()
                        .findAccessibilityNodeInfosByViewId("com.kwmobile.hometiming:id/account_delete_scroll");
                check(!scroll.isEmpty(), "delete explanation has scroll container");
                scroll.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);idle();
            }
            check(submit != null, "delete action reachable after scrolling long explanation");
            submit.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);idle();snap("delete-empty-password");
            check(!getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByViewId("com.kwmobile.hometiming:id/account_delete_password").isEmpty(), "empty delete password retains form");
            dismissDialog(false);
            check(main(() -> live.session != null), "delete account canceled without data change");
            clickText(getTargetContext().getString(R.string.preview_copy_183));
            snap("logout-confirm"); dismissDialog(false);
            check(main(() -> live.session != null), "logout canceled retains current session");
            go("family", ""); snap("family-owner"); bottom(); snap("family-actions");
            clickText(getTargetContext().getString(R.string.ui_invite_replace));
            dismissDialog(false); check(main(() -> ui.state.invite.equals("123456")), "replace invitation canceled without changing code");
            go("preferences", "join");
            runOnMainSync(() -> ((EditText)find(activity.getWindow().getDecorView(), EditText.class)).setText("12"));
            clickText(getTargetContext().getString(R.string.preview_copy_242));
            check(main(() -> ((EditText)find(activity.getWindow().getDecorView(), EditText.class)).getError() != null), "join invalid code inline");
            snap("join-errors");
            go("preferences", "notifications"); snap("notifications"); bottom(); snap("notifications-device");
            clickText(getTargetContext().getString(R.string.ui_notifications_details)); snap("notification-details"); dismissDialog(false);
            go("preferences", "region"); snap("region-owner");
            runOnMainSync(() -> {
                Object request = constructRequest(); set(activity.houseLocation, "houseLocationRequest", request);
                invokeLocation("showHouseLocationProgress", new Class<?>[]{int.class}, R.string.house_location_loading);
            }); idle();
            check(main(() -> !activity.findViewById(R.id.house_location_button).isEnabled()), "pending location disables repeat request");
            check(main(() -> activity.findViewById(R.id.house_location_status).getVisibility() == View.VISIBLE), "location progress persists inline");
            snap("region-loading"); click(R.id.house_location_cancel);
            check(main(() -> !activity.houseLocation.active() && activity.findViewById(R.id.house_location_cancel).getVisibility() == View.GONE), "cancel removes pending location and progress");
            runOnMainSync(() -> {
                set(activity.houseLocation, "houseLocationRequest", constructRequest());
                invokeLocation("showHouseLocationProgress", new Class<?>[]{int.class}, R.string.house_location_loading);
            }); back();
            check(main(() -> !activity.houseLocation.active()), "navigation cancels late location result");
            runOnMainSync(() -> live.house = new House("flow-probe", "flow-other", 2, 3, 2, null, null, null, "123456"));
            go("preferences", "region");
            check(main(() -> activity.findViewById(R.id.house_location_button) == null), "member cannot set shared region"); snap("region-member");
            go("family", ""); snap("family-member");
            runOnMainSync(() -> { set(live, "itemCache", true); ui.state.scenario = "offline"; });
            go("items", ""); snap("items-offline");
            runOnMainSync(() -> { ui.state.store.items.clear(); ui.state.scenario = "normal"; set(live, "itemCache", false); });
            go("items", ""); snap("items-empty");
            check(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null, "no server account created");
            success = true;
        } catch (Throwable error) { log.append("FAIL ").append(error).append('\n'); }
        finally {
            if (activity != null) runOnMainSync(() -> { new ViewModelProvider(activity).get(WeatherViewModel.class).setTestMode(oldWeather); activity.finish(); });
            app.unregisterActivityLifecycleCallbacks(lifecycle);
        }
        result.putString("stream", log + "FLOW UI " + (success ? "PASS" : "FAIL") + ": memory data, no Firebase writes\n");
        finish(success ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private void invokeLocation(String name, Class<?>[] types, Object... args) {
        try { Method method = HouseLocationController.class.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(activity.houseLocation, args); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private Object constructRequest() { try { Class<?> c=Class.forName(HouseLocationController.class.getName()+"$HouseLocationRequest"); Constructor<?> ctor=c.getDeclaredConstructor(int.class);ctor.setAccessible(true);return ctor.newInstance(1); }catch(Exception e){throw new RuntimeException(e);} }
    private void go(String page, String id) { runOnMainSync(() -> invoke("go",new Class<?>[]{String.class,String.class},page,id));idle(); }
    private String route() { return main(() -> ui.state.route.key()); }
    private void systemBack() {getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);idle();}
    private void back() { runOnMainSync(() -> invoke("back"));idle(); }
    private void click(int id) { runOnMainSync(() -> { View v=activity.findViewById(id);check(v!=null,"action exists "+id);v.performClick(); });idle(); }
    private void clickText(String text) { runOnMainSync(() -> { View v=text(activity.getWindow().getDecorView(),text);check(v!=null,"action exists "+text);while(!v.isClickable())v=(View)v.getParent();v.performClick(); });idle(); }
    private void clickDescription(String description) { runOnMainSync(() -> { View v=description(activity.getWindow().getDecorView(),description);check(v!=null,"navigation exists "+description);v.performClick(); });idle(); }
    private void dismissDialog(boolean positive) { idle(); android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
        String id=positive?"android:id/button1":"android:id/button2";
        java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(id);
        if(nodes.isEmpty() && !positive) nodes=root.findAccessibilityNodeInfosByViewId("android:id/button1");
        check(!nodes.isEmpty(),"dialog action reachable");check(nodes.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),"dialog action clicked");idle(); }
    private View text(View v,String s) { if(v instanceof TextView && ((TextView)v).getText().toString().equals(s))return v; if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View x=text(((ViewGroup)v).getChildAt(i),s);if(x!=null)return x;}return null; }
    private View description(View v,String s) { if(s.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View x=description(((ViewGroup)v).getChildAt(i),s);if(x!=null)return x;}return null; }
    private View find(View v,Class<?> type) { if(type.isInstance(v))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View x=find(((ViewGroup)v).getChildAt(i),type);if(x!=null)return x;}return null; }
    private int countButtons(View v) { int n=v instanceof com.google.android.material.button.MaterialButton?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=countButtons(((ViewGroup)v).getChildAt(i));return n; }
    private void bottom() {runOnMainSync(() -> {
        androidx.core.widget.NestedScrollView scroll=activity.findViewById(R.id.preview_scroll);
        scroll.scrollTo(0, Math.max(0, scroll.getChildAt(0).getHeight()+scroll.getPaddingTop()+scroll.getPaddingBottom()-scroll.getHeight()));
    });idle();}
    private void snap(String name) throws Exception {idle();Bitmap bitmap=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),prefix+"-"+name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();log.append("CAPTURE ").append(name).append('\n');Bundle progress=new Bundle();progress.putString("step",name);sendStatus(0,progress);}
    private void idle() {waitForIdleSync();SystemClock.sleep(400);}
    private void check(boolean value,String label) {if(!value)throw new AssertionError(label);log.append("PASS ").append(label).append('\n');}
    private <T>T main(java.util.function.Supplier<T> call){java.util.concurrent.atomic.AtomicReference<T> out=new java.util.concurrent.atomic.AtomicReference<>();runOnMainSync(()->out.set(call.get()));return out.get();}
    private void invoke(String name){invoke(name,new Class<?>[]{});}
    private void invoke(String name,Class<?>[] types,Object... args){try{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}}
    private static Object get(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new RuntimeException(e);}}
}
