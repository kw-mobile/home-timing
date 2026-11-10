package com.kwmobile.hometiming.ui.main;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.graphics.Insets;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.data.preview.PreviewStore.Item;
import com.kwmobile.hometiming.databinding.ActivityMainBinding;
import com.kwmobile.hometiming.ui.component.FluidMotion;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Complete native design walkthrough. All mutations are restricted to local fixtures. */
public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private DesignPreviewViewModel vm;
    private View screen;
    private boolean syncingNavigation;
    private final BroadcastReceiver clock=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) { updateWeather(); }
    };
    private DesignPreviewViewModel.State s() {return vm.state;}
    private int color(int id) {return ContextCompat.getColor(this,id);}
    private int dp(float value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    private boolean rootPage() {return isTab(s().route.screen);}
    private static boolean isTab(String name) {return name.equals("home")||name.equals("items")||name.equals("calendar")||name.equals("settings");}

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        binding=ActivityMainBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        vm=new ViewModelProvider(this).get(DesignPreviewViewModel.class);vm.restore(saved);
        configureInsets();
        binding.mainContent.setBackgroundColor(color(R.color.ht_background));
        binding.systemNavigationBackdrop.getLayoutParams().height=dp(16);
        binding.navigationContainer.setClipToOutline(true);
        binding.navigationContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view,Outline outline) {outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),view.getHeight()/2f);}
        });
        binding.glassBackdrop.setSource(binding.mainContent);
        binding.getRoot().getViewTreeObserver().addOnScrollChangedListener(binding.glassBackdrop::refresh);
        binding.bottomNavigation.setItemActiveIndicatorEnabled(false);
        binding.bottomNavigation.setBackgroundResource(android.R.color.transparent);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            if(syncingNavigation)return true;
            capture();vm.tab(item.getItemId()==R.id.nav_items?"items":item.getItemId()==R.id.nav_calendar?"calendar":item.getItemId()==R.id.nav_settings?"settings":"home");render();return true;
        });
        binding.backButton.setOnClickListener(v -> back());
        binding.galleryButton.setVisibility(BuildConfig.DEBUG?View.VISIBLE:View.GONE);
        binding.galleryButton.setOnClickListener(v -> go("gallery"));
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {back();}
        });
        if(saved==null)readPreviewIntent(getIntent());render();
    }
    @Override protected void onNewIntent(Intent intent) {super.onNewIntent(intent);setIntent(intent);capture();readPreviewIntent(intent);render();}
    private void readPreviewIntent(Intent intent) {
        if(!BuildConfig.DEBUG)return;
        String route=intent.getStringExtra("preview_screen");
        if(route!=null && java.util.Arrays.asList("home","items","calendar","settings","login","signup","reset","editor","detail","weather","family","preferences","gallery").contains(route)) {
            s().back.clear();s().route=new DesignPreviewViewModel.Route(route,intent.getStringExtra("preview_id")==null?"":intent.getStringExtra("preview_id"));
            if(route.equals("editor")) {vm.edit(s().route.id);s().back.clear();}
            if(route.equals("detail")&&s().route.id.isEmpty())s().route=new DesignPreviewViewModel.Route("detail","filter");
        }
        String scenario=intent.getStringExtra("preview_state");if(scenario!=null && java.util.Arrays.asList("normal","empty","error","loading","offline").contains(scenario))s().scenario=scenario;
        String weather=intent.getStringExtra("preview_weather");if(weather!=null && java.util.Arrays.asList("auto","morning","day","evening","night").contains(weather))s().weather=weather;
        String condition=intent.getStringExtra("preview_condition");if(condition!=null && java.util.Arrays.asList("clear","clouds","rain").contains(condition))s().condition=condition;
    }
    private void configureInsets() {
        boolean light=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller=WindowCompat.getInsetsController(getWindow(),binding.getRoot());
        controller.setAppearanceLightStatusBars(light);controller.setAppearanceLightNavigationBars(light);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(color(R.color.ht_background));
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(),(view,insets) -> {
            Insets safe=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            int ime=insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            view.setPadding(safe.left,safe.top,safe.right,Math.max(safe.bottom,ime));
            return WindowInsetsCompat.CONSUMED;
        });
    }
    @Override protected void onStart() {
        super.onStart();IntentFilter filter=new IntentFilter(Intent.ACTION_TIME_TICK);filter.addAction(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        ContextCompat.registerReceiver(this,clock,filter,ContextCompat.RECEIVER_NOT_EXPORTED);updateWeather();
    }
    @Override protected void onStop() {capture();unregisterReceiver(clock);super.onStop();}
    @Override protected void onSaveInstanceState(@NonNull Bundle out) {capture();vm.save(out);super.onSaveInstanceState(out);}
    private void capture() {
        if(screen==null)return;
        NestedScrollView scroll=screen.findViewById(R.id.preview_scroll);
        if(scroll!=null)s().scrolls.put(s().route.key(),scroll.getScrollY());
    }
    private void go(String name) {go(name,"");}
    private void go(String name,String id) {capture();hideKeyboard();vm.navigate(name,id);render();}
    private void edit(String id) {capture();vm.edit(id);render();}
    private void back() {
        if(s().route.screen.equals("editor")&&s().draft!=null&&s().draft.dirty) {
            confirm(getString(R.string.preview_copy_001),getString(R.string.preview_copy_002),getString(R.string.preview_copy_003),() -> {s().draft=null;finishBack();});return;
        }
        if(s().route.screen.equals("home")) {
            View sheet=screen.findViewById(R.id.preview_sheet);
            if(sheet!=null&&BottomSheetBehavior.from(sheet).getState()!=BottomSheetBehavior.STATE_HIDDEN) {BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_HIDDEN);return;}
        }
        finishBack();
    }
    private void finishBack() {capture();hideKeyboard();if(vm.back())render();else finish();}
    private void hideKeyboard() {((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(binding.getRoot().getWindowToken(),0);}
    private void render() {
        // These are simulated auth results. Never offer to save demo credentials to the OS.
        if(screen!=null && screen.findViewById(R.id.auth_email)!=null) {
            android.view.autofill.AutofillManager autofill=getSystemService(android.view.autofill.AutofillManager.class);
            if(autofill!=null)autofill.cancel();
        }
        String name=s().route.screen;
        int layout;
        switch(name) {
            case "login":layout=R.layout.screen_login;break;case "signup":layout=R.layout.screen_signup;break;
            case "reset":layout=R.layout.screen_reset;break;case "items":layout=R.layout.screen_items;break;
            case "calendar":layout=R.layout.screen_calendar;break;case "editor":layout=R.layout.screen_editor;break;
            case "detail":layout=R.layout.screen_detail;break;case "weather":layout=R.layout.screen_weather;break;
            case "settings":layout=R.layout.screen_settings;break;case "family":layout=R.layout.screen_family;break;
            case "preferences":layout=R.layout.screen_preferences;break;case "gallery":layout=R.layout.screen_gallery;break;
            default:layout=R.layout.screen_home;
        }
        screen=getLayoutInflater().inflate(layout,binding.screenContainer,false);
        binding.screenContainer.removeAllViews();binding.screenContainer.addView(screen);
        boolean root=rootPage();
        binding.backButton.setVisibility(root?View.GONE:View.VISIBLE);
        binding.brandIcon.setVisibility(root?View.VISIBLE:View.GONE);
        binding.toolbarTitle.setText(root?getString(R.string.preview_copy_004):toolbarTitle(name));
        binding.navigationContainer.setVisibility(root?View.VISIBLE:View.GONE);
        binding.systemNavigationBackdrop.setVisibility(root?View.VISIBLE:View.GONE);
        if(root) {
            syncingNavigation=true;
            binding.bottomNavigation.setSelectedItemId(name.equals("items")?R.id.nav_items:name.equals("calendar")?R.id.nav_calendar:name.equals("settings")?R.id.nav_settings:R.id.nav_home);
            syncingNavigation=false;
        }
        NestedScrollView scroll=screen.findViewById(R.id.preview_scroll);
        if(scroll!=null) {
            scroll.setPadding(0,0,0,root?dp(100):dp(24));
            int y=s().scrolls.getOrDefault(s().route.key(),0);scroll.post(() -> scroll.scrollTo(0,y));
        }
        switch(name) {
            case "login":case "signup":case "reset":bindAuth(name);break;
            case "items":bindItems();break;case "calendar":bindCalendar();break;
            case "editor":bindEditor();break;case "detail":bindDetail();break;
            case "weather":bindWeather();break;case "settings":bindSettings();break;
            case "family":bindFamily();break;case "preferences":bindPreferences();break;
            case "gallery":bindGallery();break;default:bindHome();break;
        }
        motion(screen);FluidMotion.enter(screen,binding.glassBackdrop::refresh);binding.glassBackdrop.refresh();
        ViewCompat.setAccessibilityPaneTitle(screen,toolbarTitle(name));
    }
    private String toolbarTitle(String name) {
        switch(name) {case "login":return getString(R.string.preview_copy_005);case "signup":return getString(R.string.preview_copy_006);case "reset":return getString(R.string.preview_copy_007);
            case "editor":return s().route.id.isEmpty()?getString(R.string.preview_copy_008):getString(R.string.preview_copy_009);case "detail":return getString(R.string.preview_copy_010);
            case "weather":return getString(R.string.preview_copy_011);case "family":return getString(R.string.preview_copy_012);case "preferences":return getString(R.string.preview_copy_013);
            case "gallery":return getString(R.string.preview_copy_014);case "items":return getString(R.string.preview_copy_015);case "calendar":return getString(R.string.preview_copy_016);case "settings":return getString(R.string.preview_copy_013);default:return getString(R.string.preview_copy_017);}
    }
    private void motion(View view) {
        if(view instanceof MaterialButton)FluidMotion.press(view);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)motion(((ViewGroup)view).getChildAt(i));
    }
    private LinearLayout box(int id) {return screen.findViewById(id);}
    private LinearLayout vertical() {LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return v;}
    private LinearLayout horizontal() {LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);v.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return v;}
    private TextView text(String value,int style) {TextView v=new com.google.android.material.textview.MaterialTextView(this);v.setTextAppearance(this,style);v.setText(value);v.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return v;}
    private TextView label(String value) {return text(value,R.style.TextAppearance_HomeTiming_Label);}
    private TextView caption(String value) {return text(value,R.style.TextAppearance_HomeTiming_Caption);}
    private void gap(LinearLayout parent,int size) {View space=new View(this);space.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);parent.addView(space,new LinearLayout.LayoutParams(1,dp(size)));}
    private void line(LinearLayout parent) {View line=new View(this);line.setBackgroundColor(color(R.color.ht_divider));line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    private void section(LinearLayout parent,String title,String subtitle) {
        TextView heading=text(title,R.style.TextAppearance_HomeTiming_Title);ViewCompat.setAccessibilityHeading(heading,true);parent.addView(heading);
        if(!subtitle.isEmpty()){gap(parent,6);parent.addView(caption(subtitle));}gap(parent,8);
    }
    private MaterialButton action(String title,boolean primary,Runnable run) {
        MaterialButton b=new MaterialButton(this,null,com.google.android.material.R.attr.materialButtonStyle);
        b.setText(title);b.setTextAppearance(this,R.style.TextAppearance_HomeTiming_BodyStrong);b.setAllCaps(false);
        b.setMinHeight(dp(52));b.setMinimumHeight(dp(52));b.setInsetTop(0);b.setInsetBottom(0);
        b.setCornerRadius(dp(14));b.setStrokeWidth(0);b.setElevation(0);b.setStateListAnimator(null);
        b.setBackgroundTintList(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{color(R.color.ht_disabled),color(primary?R.color.ht_primary:R.color.ht_container)}));
        b.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{color(R.color.ht_on_disabled),color(primary?R.color.ht_on_primary:R.color.ht_text)}));
        b.setOnClickListener(v -> run.run());FluidMotion.press(b);return b;
    }
    private void fullAction(LinearLayout parent,String title,boolean primary,Runnable run) {MaterialButton b=action(title,primary,run);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);parent.addView(b,lp);}
    private MaterialButton smallAction(String title,Runnable run) {MaterialButton b=action(title,false,run);b.setTextSize(14);b.setMinWidth(dp(48));b.setMinimumWidth(dp(48));b.setPadding(dp(12),0,dp(12),0);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));return b;}
    private void chips(LinearLayout parent,String[] values,String selected,java.util.function.Consumer<String> pick) {
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout group=horizontal();group.setLayoutParams(new FrameLayout.LayoutParams(-2,-2));
        for(String value:values) {
            MaterialButton b=smallAction(value,() -> {if(!value.equals(selected))pick.accept(value);});
            b.setBackgroundTintList(ColorStateList.valueOf(color(value.equals(selected)?R.color.ht_text:R.color.ht_container)));
            b.setTextColor(color(value.equals(selected)?R.color.ht_background:R.color.ht_text));
            b.setSelected(value.equals(selected));ViewCompat.setStateDescription(b,value.equals(selected)?getString(R.string.preview_copy_018):getString(R.string.preview_copy_019));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(48));lp.rightMargin=dp(8);group.addView(b,lp);
        }
        scroll.addView(group);parent.addView(scroll);gap(parent,12);
    }
    private LinearLayout settingsRow(LinearLayout parent,String title,String description,int icon,Runnable run) {
        LinearLayout row=horizontal();row.setMinimumHeight(dp(76));row.setPadding(0,dp(12),0,dp(12));
        if(icon!=0){ImageView image=new androidx.appcompat.widget.AppCompatImageView(this);image.setImageResource(icon);image.setImageTintList(ColorStateList.valueOf(color(R.color.ht_text)));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(22),dp(22));lp.rightMargin=dp(16);row.addView(image,lp);}
        LinearLayout copy=vertical();copy.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));copy.addView(text(title,R.style.TextAppearance_HomeTiming_BodyStrong));
        if(!description.isEmpty()){gap(copy,4);copy.addView(caption(description));}row.addView(copy);
        if(run!=null){ImageView arrow=new androidx.appcompat.widget.AppCompatImageView(this);arrow.setImageResource(R.drawable.ic_arrow);arrow.setImageTintList(ColorStateList.valueOf(color(R.color.ht_icon_secondary)));arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow,new LinearLayout.LayoutParams(dp(16),dp(16)));android.util.TypedValue feedback=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,feedback,true);row.setBackgroundResource(feedback.resourceId);row.setFocusable(true);row.setOnClickListener(v -> run.run());}
        parent.addView(row);return row;
    }
    private String date(LocalDate day) {return day.format(DateTimeFormatter.ofPattern(getString(R.string.preview_copy_020),Locale.KOREAN));}
    private String status(Item item) {long d=ChronoUnit.DAYS.between(PreviewStore.today(),item.due);return d<0?Math.abs(d)+getString(R.string.preview_copy_021):d==0?getString(R.string.preview_copy_017):"D−"+d;}
    private int statusColor(Item item) {long d=ChronoUnit.DAYS.between(PreviewStore.today(),item.due);return d<=0?R.color.ht_error:d<=3?R.color.ht_due_soon:R.color.ht_text_secondary;}
    private int icon(Item item) {return item.chore?(item.category.equals(getString(R.string.preview_copy_092))?R.drawable.ic_clean:R.drawable.ic_wash):item.name.contains(getString(R.string.preview_copy_022))?R.drawable.ic_brush:item.category.equals(getString(R.string.preview_copy_023))?R.drawable.ic_filter:R.drawable.ic_box;}
    @android.annotation.SuppressLint("ClickableViewAccessibility") // Native View handles taps; this listener consumes horizontal swipes only.
    private void itemRow(LinearLayout parent,Item item,boolean swipe) {
        View row=getLayoutInflater().inflate(R.layout.component_item_row,parent,false);
        ((TextView)row.findViewById(R.id.row_title)).setText(item.name);
        ((TextView)row.findViewById(R.id.row_meta)).setText(getString(R.string.preview_item_meta,item.location.isEmpty()?item.category:item.location,item.chore?getString(R.string.preview_copy_024):getString(R.string.preview_cycle_days,item.cycle)));
        TextView status=row.findViewById(R.id.row_status);status.setText(status(item));status.setTextColor(color(statusColor(item)));
        ((ImageView)row.findViewById(R.id.row_icon)).setImageResource(icon(item));
        row.setOnClickListener(v -> go("detail",item.id));
        if(swipe)row.setOnTouchListener(new View.OnTouchListener() {
            float x,y;boolean horizontal;
            @Override public boolean onTouch(View v,MotionEvent event) {
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){x=event.getRawX();y=event.getRawY();horizontal=false;}
                else if(event.getActionMasked()==MotionEvent.ACTION_MOVE) {
                    float dx=event.getRawX()-x,dy=event.getRawY()-y;
                    if(Math.abs(dx)>dp(16)&&Math.abs(dx)>Math.abs(dy)*1.5f)horizontal=true;
                    if(horizontal){v.setPressed(false);v.cancelLongPress();v.getParent().requestDisallowInterceptTouchEvent(true);v.setTranslationX(Math.max(-dp(110),Math.min(dp(24),dx*.55f)));return true;}
                }else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL) {
                    boolean complete=event.getActionMasked()==MotionEvent.ACTION_UP&&horizontal&&event.getRawX()-x<-dp(90);
                    if(horizontal){v.setPressed(false);v.cancelLongPress();}
                    if(FluidMotion.enabled(v))v.animate().translationX(0).setDuration(180).start();else v.setTranslationX(0);
                    if(complete)askComplete(item);return horizontal;
                }
                return false;
            }
        });
        parent.addView(row);line(parent);
    }
    private boolean stateMessage(LinearLayout parent,Runnable retry) {
        String state=s().scenario;
        if(state.equals("normal"))return false;
        if(state.equals("offline")){parent.addView(caption(getString(R.string.preview_copy_026)));gap(parent,16);return false;}
        gap(parent,24);
        if(state.equals("empty")&&s().route.screen.equals("weather")) {
            section(parent,getString(R.string.preview_forecast_empty),getString(R.string.preview_forecast_empty_help));
            fullAction(parent,getString(R.string.preview_copy_031),true,() -> {s().scenario="normal";retry.run();});return true;
        }
        if(state.equals("loading")) {
            android.widget.ProgressBar progress=new android.widget.ProgressBar(this);parent.addView(progress,new LinearLayout.LayoutParams(dp(28),dp(28)));gap(parent,16);
            parent.addView(label(getString(R.string.preview_copy_027)));fullAction(parent,getString(R.string.preview_copy_028),false,() -> {s().scenario="normal";retry.run();});
        }else if(state.equals("error")){section(parent,getString(R.string.preview_copy_029),getString(R.string.preview_copy_030));fullAction(parent,getString(R.string.preview_copy_031),true,() -> {s().scenario="normal";retry.run();});}
        else {section(parent,getString(R.string.preview_copy_032),getString(R.string.preview_copy_033));fullAction(parent,getString(R.string.preview_copy_008),true,() -> edit(""));}
        return true;
    }
    private void bindHome() {
        ((TextView)screen.findViewById(R.id.home_date)).setText(PreviewStore.today().format(DateTimeFormatter.ofPattern(getString(R.string.preview_copy_034),Locale.KOREAN)));
        View homePhoto=screen.findViewById(R.id.weather_hero);
        ViewGroup.LayoutParams photoSize=homePhoto.getLayoutParams();photoSize.height=dp(260);homePhoto.setLayoutParams(photoSize);
        TextView homeTemperature=screen.findViewById(R.id.weather_temperature);homeTemperature.setTextSize(64);androidx.core.widget.TextViewCompat.setLineHeight(homeTemperature,Math.round(72*getResources().getDisplayMetrics().scaledDensity));
        LinearLayout photoCopy=(LinearLayout)screen.findViewById(R.id.weather_location).getParent();photoCopy.setPadding(dp(20),dp(16),dp(20),dp(16));
        updateWeather();screen.findViewById(R.id.weather_hero).setOnClickListener(v -> go("weather"));
        screen.findViewById(R.id.weather_hero).setContentDescription(getString(R.string.preview_copy_035));screen.findViewById(R.id.weather_hero).setFocusable(true);
        LinearLayout rows=box(R.id.home_rows);
        LinearLayout heading=horizontal();LinearLayout headingCopy=vertical();headingCopy.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        TextView title=text(getString(R.string.preview_copy_036),R.style.TextAppearance_HomeTiming_Title);headingCopy.addView(title);
        TextView count=caption("");headingCopy.addView(count);heading.addView(headingCopy);heading.addView(smallAction(getString(R.string.preview_copy_037),() -> edit("")));ViewCompat.setAccessibilityHeading(title,true);box(R.id.home_sheet_heading).addView(heading);

        if(!stateMessage(rows,this::render)) {
            List<Item> due=new ArrayList<>(),soon=new ArrayList<>();
            for(Item item:s().store.sorted())if(!item.due.isAfter(PreviewStore.today()))due.add(item);else if(!item.due.isAfter(PreviewStore.today().plusDays(7)))soon.add(item);
            count.setText(getString(R.string.preview_home_due_count,due.size()));
            if(due.isEmpty())rows.addView(label(getString(R.string.preview_copy_040)));else for(Item item:due)itemRow(rows,item,false);
            if(!s().permissionGranted){gap(rows,12);settingsRow(rows,getString(R.string.preview_copy_041),getString(R.string.preview_copy_042),R.drawable.ic_bell,() -> go("preferences","notifications"));line(rows);}
            gap(rows,28);section(rows,getString(R.string.preview_copy_043),soon.size()+getString(R.string.preview_copy_044));for(Item item:soon)itemRow(rows,item,false);
        }
        gap(rows,16);rows.addView(caption(getString(R.string.preview_copy_045)));
        View sheet=screen.findViewById(R.id.preview_sheet);BottomSheetBehavior<View> behavior=BottomSheetBehavior.from(sheet);
        View handle=screen.findViewById(R.id.sheet_handle);
        LinearLayout summary=box(R.id.home_task_summary);int todayCount=0,lateCount=0,weekCount=0;
        for(Item item:s().store.sorted()) {
            long days=ChronoUnit.DAYS.between(PreviewStore.today(),item.due);
            if(days==0)todayCount++;else if(days<0)lateCount++;else if(days<=7)weekCount++;
        }
        if(s().scenario.equals("empty")){todayCount=0;lateCount=0;weekCount=0;}
        boolean unavailable=s().scenario.equals("error")||s().scenario.equals("loading");
        LinearLayout summaryRow=settingsRow(summary,getString(R.string.intent_home_tasks),unavailable?getString(R.string.intent_home_loading):getString(R.string.intent_home_counts,todayCount,lateCount,weekCount),R.drawable.ic_calendar,() -> behavior.setState(BottomSheetBehavior.STATE_EXPANDED));
        summaryRow.setMinimumHeight(dp(64));summaryRow.setPadding(0,dp(8),0,dp(8));
        boolean weatherUnknown=!s().scenario.equals("normal");
        LinearLayout proposalRow=settingsRow(box(R.id.home_weather_suggestion),getString(R.string.intent_home_weather),getString(weatherUnknown?R.string.intent_home_weather_unknown:s().condition.equals("rain")?R.string.intent_home_weather_rain:R.string.intent_home_weather_clear),R.drawable.ic_wash,() -> go("weather"));
        proposalRow.setMinimumHeight(dp(64));proposalRow.setPadding(0,dp(8),0,dp(8));
        View reopen=screen.findViewById(R.id.home_reopen);
        summary.setVisibility(s().sheetHidden?View.GONE:View.VISIBLE);
        reopen.setVisibility(s().sheetHidden?View.VISIBLE:View.GONE);
        reopen.setOnClickListener(v -> behavior.setState(BottomSheetBehavior.STATE_COLLAPSED));FluidMotion.press(reopen);
        androidx.appcompat.widget.AppCompatImageButton close=new androidx.appcompat.widget.AppCompatImageButton(this);
        close.setId(View.generateViewId());close.setImageResource(R.drawable.ic_close);close.setContentDescription(getString(R.string.preview_sheet_close));
        android.util.TypedValue closeFeedback=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,closeFeedback,true);close.setBackgroundResource(closeFeedback.resourceId);
        close.setPadding(dp(12),dp(12),dp(12),dp(12));close.setOnClickListener(v -> behavior.setState(BottomSheetBehavior.STATE_HIDDEN));FluidMotion.press(close);
        LinearLayout.LayoutParams closeSize=new LinearLayout.LayoutParams(dp(48),dp(48));closeSize.leftMargin=dp(4);heading.addView(close,closeSize);
        sheet.post(() -> {if(!sheet.isAttachedToWindow())return;int h=((View)sheet.getParent()).getHeight();int previousPeek=Math.min(Math.max(dp(265),h-dp(350)),Math.max(dp(220),h-dp(90)));behavior.setPeekHeight(Math.min(Math.max(dp(180),previousPeek/2),Math.max(dp(120),h-dp(48))),false);behavior.setState(s().sheetHidden?BottomSheetBehavior.STATE_HIDDEN:s().expanded?BottomSheetBehavior.STATE_EXPANDED:BottomSheetBehavior.STATE_COLLAPSED);});
        handle.setOnClickListener(v -> behavior.setState(behavior.getState()==BottomSheetBehavior.STATE_EXPANDED?BottomSheetBehavior.STATE_COLLAPSED:BottomSheetBehavior.STATE_EXPANDED));
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override public void onStateChanged(@NonNull View bottom,int state) {
                if(state==BottomSheetBehavior.STATE_EXPANDED||state==BottomSheetBehavior.STATE_COLLAPSED||state==BottomSheetBehavior.STATE_HIDDEN) {
                    s().expanded=state==BottomSheetBehavior.STATE_EXPANDED;s().sheetHidden=state==BottomSheetBehavior.STATE_HIDDEN;
                    reopen.setVisibility(s().sheetHidden?View.VISIBLE:View.GONE);summary.setVisibility(s().sheetHidden?View.GONE:View.VISIBLE);
                }
                handle.setContentDescription(s().expanded?getString(R.string.preview_copy_046):getString(R.string.preview_copy_047));ViewCompat.setStateDescription(handle,s().expanded?getString(R.string.preview_copy_048):getString(R.string.preview_copy_049));
                View hero=screen.findViewById(R.id.hero_scroll);if(hero!=null)hero.setImportantForAccessibility(s().expanded?View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
            @Override public void onSlide(@NonNull View bottom,float offset){binding.glassBackdrop.refresh();}
        });
    }
    private void bindItems() {
        LinearLayout controls=box(R.id.item_controls);LinearLayout add=horizontal();TextView count=label(getString(R.string.preview_copy_050));count.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));add.addView(count);add.addView(smallAction(getString(R.string.preview_copy_037),() -> edit("")));controls.addView(add);gap(controls,16);
        chips(controls,new String[]{getString(R.string.preview_copy_051),getString(R.string.preview_copy_023),getString(R.string.preview_copy_052),getString(R.string.preview_copy_053)},s().filter,value -> {capture();s().filter=value;render();});
        LinearLayout rows=box(R.id.item_rows);if(stateMessage(rows,this::render))return;
        int countItems=0;for(Item item:s().store.sorted())if(!item.chore&&(s().filter.equals(getString(R.string.preview_copy_051))||s().filter.equals(item.category))){itemRow(rows,item,true);countItems++;}
        if(s().scenario.equals("normal"))count.setText(getString(R.string.preview_item_count,s().filter,countItems));
        if(countItems==0){gap(rows,32);section(rows,getString(R.string.preview_copy_054),getString(R.string.preview_copy_055));fullAction(rows,getString(R.string.preview_copy_056),true,() -> edit(""));}
        else {gap(rows,20);rows.addView(caption(getString(R.string.preview_copy_057)));}
    }
    private void bindCalendar() {
        LinearLayout controls=box(R.id.calendar_controls);LinearLayout month=horizontal();
        MaterialButton previous=smallAction("‹",() -> {capture();s().month=s().month.minusMonths(1);render();});previous.setContentDescription(getString(R.string.preview_previous_month));month.addView(previous);
        TextView heading=text(s().month.format(DateTimeFormatter.ofPattern(getString(R.string.preview_copy_058),Locale.KOREAN)),R.style.TextAppearance_HomeTiming_Title);heading.setGravity(Gravity.CENTER);heading.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));month.addView(heading);
        MaterialButton next=smallAction("›",() -> {capture();s().month=s().month.plusMonths(1);render();});next.setContentDescription(getString(R.string.preview_next_month));month.addView(next);controls.addView(month);
        GridLayout grid=screen.findViewById(R.id.calendar_grid);String[] days={getString(R.string.preview_copy_059),getString(R.string.preview_copy_060),getString(R.string.preview_copy_061),getString(R.string.preview_copy_062),getString(R.string.preview_copy_063),getString(R.string.preview_copy_064),getString(R.string.preview_copy_065)};
        for(String day:days){TextView cell=caption(day);cell.setGravity(Gravity.CENTER);GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=dp(40);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);grid.addView(cell,lp);}
        int lead=s().month.atDay(1).getDayOfWeek().getValue()%7;
        for(int i=0;i<42;i++) {
            LocalDate day=s().month.atDay(1).plusDays(i-lead);boolean current=YearMonth.from(day).equals(s().month);
            LinearLayout cell=vertical();cell.setGravity(Gravity.CENTER);cell.setPadding(0,dp(3),0,dp(3));
            TextView number=text(String.valueOf(day.getDayOfMonth()),R.style.TextAppearance_HomeTiming_Body);number.setGravity(Gravity.CENTER);number.setTextSize(16);cell.addView(number);
            boolean event=false;for(Item item:s().store.items)if(item.due.equals(day))event=true;
            TextView dot=caption(event&&!s().scenario.equals("empty")?"•":" ");dot.setGravity(Gravity.CENTER);dot.setTextSize(12);cell.addView(dot);
            boolean selected=day.equals(s().selected);
            if(selected){cell.setBackgroundResource(R.drawable.bg_selected);number.setTextColor(color(R.color.ht_background));dot.setTextColor(color(R.color.ht_background));}
            else if(!current){number.setTextColor(color(R.color.ht_icon_secondary));number.setAlpha(.65f);}
            else if(day.equals(PreviewStore.today()))number.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this,R.font.pretendard_semibold));
            cell.setContentDescription(date(day)+(selected?getString(R.string.preview_copy_066):"")+(event?getString(R.string.preview_copy_067):""));cell.setSelected(selected);cell.setFocusable(true);
            cell.setOnClickListener(v -> {capture();s().selected=day;s().month=YearMonth.from(day);render();});
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=dp(54);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);grid.addView(cell,lp);
        }
        LinearLayout rows=box(R.id.calendar_rows);LinearLayout dayHeading=horizontal();TextView selected=text(date(s().selected),R.style.TextAppearance_HomeTiming_Title);selected.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));dayHeading.addView(selected);dayHeading.addView(smallAction(getString(R.string.preview_copy_017),() -> {s().selected=PreviewStore.today();s().month=YearMonth.from(s().selected);render();}));rows.addView(dayHeading);gap(rows,8);
        if(stateMessage(rows,this::render))return;
        int count=0;for(Item item:s().store.sorted())if(item.due.equals(s().selected)){itemRow(rows,item,false);count++;}
        if(count==0){gap(rows,20);rows.addView(label(getString(R.string.preview_copy_068)));gap(rows,6);rows.addView(caption(getString(R.string.preview_copy_069)));}
    }
    private void bindAuth(String mode) {
        int[] ids={R.id.auth_email,R.id.auth_password,R.id.auth_name,R.id.auth_confirm};
        for(int id:ids) {
            EditText field=screen.findViewById(id);if(field==null)continue;
            String key=mode+":"+id;field.setText(s().authFields.getOrDefault(key,""));
            field.addTextChangedListener(watcher(value -> s().authFields.put(key,value)));
        }
        EditText password=screen.findViewById(R.id.auth_password);
        if(password!=null) {
            password.setInputType(InputType.TYPE_CLASS_TEXT|(s().passwordVisible?InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:InputType.TYPE_TEXT_VARIATION_PASSWORD));
            LinearLayout options=box(R.id.auth_options);LinearLayout row=horizontal();
            MaterialButton show=smallAction(s().passwordVisible?getString(R.string.preview_copy_070):getString(R.string.preview_copy_071),() -> {s().passwordVisible=!s().passwordVisible;capture();render();});show.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            row.addView(show,new LinearLayout.LayoutParams(0,-2,1));
            if(mode.equals("login")){MaterialButton reset=smallAction(getString(R.string.preview_copy_072),() -> go("reset"));reset.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));row.addView(reset,new LinearLayout.LayoutParams(0,-2,1));}
            options.addView(row);
        }
        MaterialButton submit=screen.findViewById(R.id.auth_submit);
        View change=screen.findViewById(R.id.auth_switch);
        if(change!=null)change.setOnClickListener(v -> go(mode.equals("login")?"signup":"login"));
        submit.setOnClickListener(v -> {
            boolean valid=validateEmail();
            if(password!=null && password.getText().toString().length()<6) {fieldError(R.id.auth_password_error,getString(R.string.preview_copy_073));valid=false;}else clearError(R.id.auth_password_error);
            if(mode.equals("signup")) {
                EditText name=screen.findViewById(R.id.auth_name);EditText repeat=screen.findViewById(R.id.auth_confirm);
                if(name.getText().toString().trim().isEmpty()){fieldError(R.id.auth_name_error,getString(R.string.preview_copy_074));valid=false;}else clearError(R.id.auth_name_error);
                if(!repeat.getText().toString().equals(password.getText().toString())) {fieldError(R.id.auth_confirm_error,getString(R.string.preview_copy_075));valid=false;}else clearError(R.id.auth_confirm_error);
            }
            if(!valid){focusFirstError();return;}
            hideKeyboard();submit.setEnabled(false);submit.setText(getString(R.string.preview_copy_076));
            String key=s().route.key();View current=screen;
            current.postDelayed(() -> {
                if(isFinishing()||isDestroyed()||screen!=current||!s().route.key().equals(key))return;
                submit.setEnabled(true);
                if(s().scenario.equals("error")||s().scenario.equals("offline")) {submit.setText(mode.equals("reset")?getString(R.string.preview_copy_031):mode.equals("signup")?getString(R.string.preview_copy_077):getString(R.string.preview_copy_005));notice(getString(R.string.preview_copy_078));return;}
                if(mode.equals("reset")) {
                    submit.setText(getString(R.string.preview_copy_079));LinearLayout feedback=box(R.id.reset_feedback);feedback.removeAllViews();gap(feedback,24);section(feedback,getString(R.string.preview_copy_080),getString(R.string.preview_copy_081));fullAction(feedback,getString(R.string.preview_copy_082),false,() -> {vm.tab("login");render();});
                } else {
                    s().authFields.clear();s().passwordVisible=false;vm.tab("home");render();notice(mode.equals("signup")?getString(R.string.preview_copy_083):getString(R.string.preview_copy_084));
                }
            },450);
        });
    }
    private boolean validateEmail() {
        EditText email=screen.findViewById(R.id.auth_email);
        boolean valid=android.util.Patterns.EMAIL_ADDRESS.matcher(email.getText().toString().trim()).matches();
        if(!valid)fieldError(R.id.auth_email_error,getString(R.string.preview_copy_085));else clearError(R.id.auth_email_error);return valid;
    }
    private void fieldError(int id,String message) {TextView view=screen.findViewById(id);if(view!=null){view.setText(message);view.setVisibility(View.VISIBLE);ViewCompat.setAccessibilityLiveRegion(view,ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);}}
    private void clearError(int id) {View view=screen.findViewById(id);if(view!=null)view.setVisibility(View.GONE);}
    private void focusFirstError() {
        int[][] fields={{R.id.auth_name_error,R.id.auth_name},{R.id.auth_email_error,R.id.auth_email},{R.id.auth_password_error,R.id.auth_password},{R.id.auth_confirm_error,R.id.auth_confirm},{R.id.edit_name_error,R.id.edit_name},{R.id.edit_cycle_error,R.id.edit_cycle}};
        for(int[] pair:fields){View error=screen.findViewById(pair[0]);View input=screen.findViewById(pair[1]);if(error!=null&&input!=null&&error.getVisibility()==View.VISIBLE){input.requestFocus();input.announceForAccessibility(((TextView)error).getText());return;}}
    }
    private TextWatcher watcher(java.util.function.Consumer<String> changed) {
        return new TextWatcher(){@Override public void beforeTextChanged(CharSequence v,int a,int b,int c){}@Override public void onTextChanged(CharSequence v,int a,int b,int c){changed.accept(v.toString());}@Override public void afterTextChanged(Editable value){}};
    }
    private void bindEditor() {
        if(s().draft==null)s().draft=new DesignPreviewViewModel.Draft();
        DesignPreviewViewModel.Draft d=s().draft;
        ((TextView)screen.findViewById(R.id.editor_heading)).setText(d.id.isEmpty()?getString(R.string.preview_copy_086):getString(R.string.preview_copy_009));
        chips(box(R.id.editor_kind),new String[]{getString(R.string.preview_copy_087),getString(R.string.preview_copy_088)},d.chore?getString(R.string.preview_copy_088):getString(R.string.preview_copy_087),value -> {
            capture();d.chore=value.equals(getString(R.string.preview_copy_088));d.cycle=d.chore?"7":"60";d.category=d.chore?getString(R.string.preview_copy_089):getString(R.string.preview_copy_053);d.weather=d.chore;d.dirty=true;render();
        });
        int[] ids={R.id.edit_name,R.id.edit_location,R.id.edit_cycle};String[] values={d.name,d.location,d.cycle};
        for(int i=0;i<ids.length;i++) {
            EditText field=screen.findViewById(ids[i]);field.setText(values[i]);final int id=ids[i];
            field.addTextChangedListener(watcher(value -> {if(id==R.id.edit_name)d.name=value;else if(id==R.id.edit_location)d.location=value;else d.cycle=value;d.dirty=true;updateDraftDuePreview();}));
        }
        LinearLayout category=box(R.id.editor_category);gap(category,16);
        settingsRow(category,getString(R.string.preview_copy_090),d.category,0,() -> choose(getString(R.string.preview_copy_091),d.chore?new String[]{getString(R.string.preview_copy_089),getString(R.string.preview_copy_092),getString(R.string.preview_copy_053)}:new String[]{getString(R.string.preview_copy_023),getString(R.string.preview_copy_052),getString(R.string.preview_copy_053)},value -> {capture();d.category=value;d.dirty=true;render();}));line(category);
        LinearLayout dates=box(R.id.editor_date);settingsRow(dates,d.chore?getString(R.string.preview_copy_093):getString(R.string.preview_copy_094),date(d.date),R.drawable.ic_calendar,() -> {
            DatePickerDialog dialog=new DatePickerDialog(this,(picker,year,month,day) -> {capture();d.date=LocalDate.of(year,month+1,day);d.dirty=true;render();},d.date.getYear(),d.date.getMonthValue()-1,d.date.getDayOfMonth());
            dialog.getDatePicker().setMaxDate(System.currentTimeMillis());dialog.show();
        });line(dates);gap(dates,8);dates.addView(caption(getString(d.chore?R.string.preview_chore_start_help:R.string.preview_start_help)));
        if(d.chore) {
            ((TextView)screen.findViewById(R.id.cycle_label)).setText(R.string.preview_chore_cycle_label);
            ((TextView)screen.findViewById(R.id.cycle_help)).setText(R.string.preview_chore_cycle_help);
            EditText cycle=screen.findViewById(R.id.edit_cycle);cycle.setEnabled(false);
            LinearLayout chore=box(R.id.editor_chore);gap(chore,20);chore.addView(label(getString(R.string.preview_copy_095)));gap(chore,8);
            String[] weekdays={getString(R.string.preview_copy_060),getString(R.string.preview_copy_061),getString(R.string.preview_copy_062),getString(R.string.preview_copy_063),getString(R.string.preview_copy_064),getString(R.string.preview_copy_065),getString(R.string.preview_copy_059)};
            chips(chore,weekdays,weekdays[d.weekday-1],value -> {
                int target=java.util.Arrays.asList(weekdays).indexOf(value)+1;
                d.weekday=target;d.dirty=true;capture();render();
            });
            SwitchMaterial sensitive=new SwitchMaterial(this);sensitive.setText(getString(R.string.preview_copy_096));sensitive.setTextColor(color(R.color.ht_text));sensitive.setMinHeight(dp(52));sensitive.setChecked(d.weather);sensitive.setOnCheckedChangeListener((button,checked) -> {d.weather=checked;d.dirty=true;});chore.addView(sensitive);
        }
        updateDraftDuePreview();
        MaterialButton submit=screen.findViewById(R.id.edit_submit);submit.setText(d.id.isEmpty()?getString(R.string.preview_copy_097):getString(R.string.preview_copy_098));
        if(s().scenario.equals("offline")){submit.setEnabled(false);fieldError(R.id.edit_cycle_error,getString(R.string.preview_copy_099));}
        submit.setOnClickListener(v -> {
            boolean valid=true;clearError(R.id.edit_name_error);clearError(R.id.edit_cycle_error);
            if(d.name.trim().isEmpty()||d.name.trim().length()>60){fieldError(R.id.edit_name_error,getString(R.string.preview_copy_100));valid=false;}
            int cycle=0;try{cycle=Integer.parseInt(d.cycle);}catch(NumberFormatException ignored){}
            if(cycle<1||cycle>730){fieldError(R.id.edit_cycle_error,getString(R.string.preview_copy_101));valid=false;}
            if(!valid){focusFirstError();return;}
            if(s().scenario.equals("error")){notice(getString(R.string.preview_copy_102));return;}
            Item item=s().store.save(d.id,d.name.trim(),d.category,d.location.trim(),d.chore,cycle,d.date,d.weather,d.weekday);
            hideKeyboard();s().draft=null;
            if(!s().back.isEmpty()&&s().back.peek().screen.equals("detail"))s().back.pop();s().route=new DesignPreviewViewModel.Route("detail",item.id);render();notice(getString(R.string.preview_copy_103));
        });
    }
    private void updateDraftDuePreview() {
        if(screen==null||s().draft==null)return;
        TextView preview=screen.findViewById(R.id.next_due_preview);if(preview==null)return;
        DesignPreviewViewModel.Draft draft=s().draft;
        try {
            int days=Integer.parseInt(draft.cycle);
            if(days<1||days>730){preview.setText("");return;}
            LocalDate next=draft.chore?draft.date.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.of(draft.weekday))):draft.date.plusDays(days);
            preview.setText(getString(R.string.preview_next_due,date(next)));
        }catch(NumberFormatException invalid){preview.setText("");}
    }
    private void bindDetail() {
        LinearLayout body=box(R.id.detail_content);Item item=s().store.find(s().route.id);
        if(item==null) {section(body,getString(R.string.preview_copy_104),getString(R.string.preview_copy_105));fullAction(body,getString(R.string.preview_copy_106),true,() -> {vm.tab("items");render();});return;}
        body.addView(caption((item.chore?getString(R.string.preview_copy_088):getString(R.string.preview_copy_087))+" / "+item.category));gap(body,12);
        TextView name=text(item.name,R.style.TextAppearance_HomeTiming_Display);ViewCompat.setAccessibilityHeading(name,true);body.addView(name);gap(body,28);
        TextView status=text(status(item),R.style.TextAppearance_HomeTiming_Number);status.setTextColor(color(statusColor(item)));body.addView(status);gap(body,6);
        body.addView(text(date(item.due),R.style.TextAppearance_HomeTiming_Title));gap(body,8);body.addView(caption(item.chore?getString(R.string.preview_copy_107):getString(R.string.preview_copy_108)));gap(body,16);
        MaterialButton complete=action(item.chore?getString(R.string.preview_copy_109):getString(R.string.preview_copy_110),true,() -> askComplete(item));body.addView(complete,new LinearLayout.LayoutParams(-1,-2));
        boolean done=!item.history.isEmpty()&&item.history.get(0).date.equals(PreviewStore.today());
        if(done||s().scenario.equals("offline")){complete.setEnabled(false);complete.setBackgroundTintList(ColorStateList.valueOf(color(R.color.ht_disabled)));complete.setTextColor(color(R.color.ht_on_disabled));gap(body,8);body.addView(caption(done?getString(R.string.preview_copy_111):getString(R.string.preview_copy_112)));}
        gap(body,32);line(body);gap(body,24);section(body,getString(R.string.preview_copy_113),"");
        settingsRow(body,getString(R.string.preview_copy_114),item.location.isEmpty()?getString(R.string.preview_copy_115):item.location,0,null);
        settingsRow(body,getString(R.string.preview_copy_116),item.chore?getString(R.string.preview_copy_117,java.time.DayOfWeek.of(item.weekday).getDisplayName(java.time.format.TextStyle.FULL,Locale.KOREAN)):item.cycle+getString(R.string.preview_copy_118),0,null);
        settingsRow(body,getString(R.string.preview_copy_094),date(item.start),0,null);
        gap(body,24);section(body,item.chore?getString(R.string.preview_copy_119):getString(R.string.preview_copy_120),item.history.size()+getString(R.string.preview_copy_121));
        if(item.history.isEmpty()){gap(body,16);body.addView(label(getString(R.string.preview_copy_122)));}
        else for(PreviewStore.History event:item.history) {settingsRow(body,date(event.date),event.person+getString(R.string.preview_copy_123),R.drawable.ic_check,null);line(body);}
        gap(body,24);LinearLayout actions=horizontal();MaterialButton edit=action(getString(R.string.preview_copy_124),false,() -> edit(item.id));MaterialButton delete=action(getString(R.string.preview_copy_125),false,() -> confirm(getString(R.string.preview_copy_126),getString(R.string.preview_copy_127),getString(R.string.preview_copy_125),() -> {s().store.delete(item.id);vm.back();render();notice(getString(R.string.preview_copy_128));}));delete.setTextColor(color(R.color.ht_error));
        actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(12);actions.addView(delete,lp);body.addView(actions);
        if(s().scenario.equals("offline")){edit.setEnabled(false);delete.setEnabled(false);}
        if(!s().owner&&!s().person.equals(item.createdBy)){delete.setEnabled(false);gap(body,8);body.addView(caption(getString(R.string.intent_delete_permission)));}
        gap(body,20);body.addView(caption(getString(R.string.preview_copy_129)));
    }
    private void askComplete(Item item) {
        if(s().scenario.equals("offline")){notice(getString(R.string.preview_copy_130));return;}
        confirm(item.chore?getString(R.string.preview_copy_131):getString(R.string.preview_copy_132),item.name+getString(R.string.preview_copy_133)+"\n"+getString(item.chore?R.string.preview_complete_chore_help:R.string.preview_complete_item_help),getString(R.string.preview_copy_134),() -> {
            if(s().scenario.equals("error")){notice(getString(R.string.preview_copy_135));return;}
            boolean result=s().store.complete(item.id);capture();render();notice(result?getString(R.string.preview_copy_136):getString(R.string.preview_copy_137));
        });
    }
    private void updateWeather() {
        if(screen==null)return;ImageView photo=screen.findViewById(R.id.weather_photo);if(photo==null)return;
        String period=s().weather.equals("auto")?HomeTimeOfDay.at(ZonedDateTime.now()).name().toLowerCase(Locale.ROOT):s().weather;
        int image;
        if(s().condition.equals("rain"))image=R.drawable.weather_rain;
        else if(s().condition.equals("clouds"))image=R.drawable.weather_clouds;
        else if(period.equals("night"))image=R.drawable.weather_night;
        else if(period.equals("evening"))image=R.drawable.weather_evening;
        else if(period.equals("morning"))image=R.drawable.weather_morning;
        else image=R.drawable.weather_day;
        photo.setImageResource(image);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if(image==R.drawable.weather_night) {
            // Place the moon in the upper right, clear of centered weather labels.
            android.graphics.drawable.Drawable nightPhoto=photo.getDrawable();
            photo.post(() -> {
                if(photo.getDrawable()!=nightPhoto||photo.getWidth()==0||photo.getHeight()==0)return;
                float width=photo.getWidth(),height=photo.getHeight();
                float sourceWidth=nightPhoto.getIntrinsicWidth(),sourceHeight=nightPhoto.getIntrinsicHeight();
                float scale=Math.max(width/sourceWidth,height/sourceHeight)*1.65f;
                float x=Math.max(width-sourceWidth*scale,Math.min(0,width*.86f-sourceWidth*.59f*scale));
                float y=Math.max(height-sourceHeight*scale,Math.min(0,height*.14f-sourceHeight*.40f*scale));
                android.graphics.Matrix crop=new android.graphics.Matrix();
                crop.setScale(scale,scale);crop.postTranslate(x,y);
                photo.setScaleType(ImageView.ScaleType.MATRIX);photo.setImageMatrix(crop);
            });
        }
        ((View)photo.getParent()).setClipToOutline(true);
        ((TextView)screen.findViewById(R.id.weather_location)).setText(s().region);
        ((TextView)screen.findViewById(R.id.weather_temperature)).setText(period.equals("night")?"12°":"18°");
        ((TextView)screen.findViewById(R.id.weather_condition)).setText(s().condition.equals("rain")?getString(R.string.preview_copy_138):s().condition.equals("clouds")?getString(R.string.preview_copy_139):period.equals("night")?getString(R.string.preview_copy_140):getString(R.string.preview_copy_141));
        String time=ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        int periodLabel=period.equals("night")?R.string.preview_copy_257:period.equals("evening")?R.string.preview_copy_256:period.equals("morning")?R.string.preview_copy_254:R.string.preview_copy_255;
        ((TextView)screen.findViewById(R.id.weather_time)).setText(s().weather.equals("auto")?getString(R.string.preview_weather_clock,time):getString(R.string.preview_weather_manual,getString(periodLabel)));
    }
    private void bindWeather() {
        updateWeather();LinearLayout content=box(R.id.weather_content);
        if(stateMessage(content,this::render))return;
        section(content,getString(R.string.preview_copy_144),getString(R.string.preview_copy_145,s().region));
        LinearLayout forecastHead=horizontal();
        TextView dayHeading=caption(getString(R.string.preview_forecast_day));forecastHead.addView(dayHeading,new LinearLayout.LayoutParams(0,-2,1));
        TextView temperatureHeading=caption(getString(R.string.preview_forecast_temperatures));temperatureHeading.setGravity(Gravity.END);forecastHead.addView(temperatureHeading,new LinearLayout.LayoutParams(0,-2,1));content.addView(forecastHead);gap(content,4);
        for(int i=0;i<3;i++) {
            LinearLayout row=horizontal();row.setMinimumHeight(dp(62));TextView day=text(i==0?getString(R.string.preview_copy_017):i==1?getString(R.string.preview_copy_146):getString(R.string.preview_copy_147),R.style.TextAppearance_HomeTiming_BodyStrong);day.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));row.addView(day);
            ImageView icon=new androidx.appcompat.widget.AppCompatImageView(this);icon.setImageResource(i==1?R.drawable.ic_cloud:s().condition.equals("rain")?R.drawable.ic_rain:R.drawable.ic_sun);icon.setImageTintList(ColorStateList.valueOf(color(R.color.ht_text)));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(icon,new LinearLayout.LayoutParams(dp(22),dp(22)));
            TextView degrees=label(i==0?"12°   21°":i==1?"11°   19°":"10°   20°");degrees.setGravity(Gravity.END);row.addView(degrees,new LinearLayout.LayoutParams(0,-2,1));row.setContentDescription((i==0?getString(R.string.preview_copy_017):i==1?getString(R.string.preview_copy_146):getString(R.string.preview_copy_147))+(i==1?getString(R.string.preview_copy_148):", "+(s().condition.equals("rain")?getString(R.string.preview_copy_138):getString(R.string.preview_copy_141)))+", "+degrees.getText());content.addView(row);line(content);
        }
        gap(content,28);boolean rain=s().condition.equals("rain"),unknown=s().scenario.equals("offline");
        section(content,getString(R.string.preview_copy_149,unknown?getString(R.string.preview_copy_150):rain?getString(R.string.preview_copy_151):getString(R.string.preview_copy_152)),getString(R.string.preview_copy_153));
        content.addView(label(unknown?getString(R.string.preview_copy_154):rain?getString(R.string.preview_copy_155):getString(R.string.preview_copy_156)));
        gap(content,28);section(content,getString(R.string.preview_copy_157),getString(R.string.preview_copy_158));
        for(Item item:s().store.sorted())if(item.chore&&item.weatherSensitive) {
            settingsRow(content,item.name,getString(R.string.preview_copy_159,date(item.due)),icon(item),() -> go("detail",item.id));
            LocalDate target=PreviewStore.today().plusDays(1);
            if(unknown)content.addView(caption(getString(R.string.preview_copy_154)));
            else if(item.proposalDismissed)content.addView(caption(getString(R.string.intent_proposal_kept)));
            else if(!rain||item.due.equals(target))content.addView(caption(getString(R.string.intent_proposal_keep_schedule)));
            else {
                content.addView(caption(getString(R.string.intent_proposal_reason,date(item.due),date(target))));gap(content,12);
                LinearLayout choices=horizontal();
                MaterialButton change=action(getString(R.string.preview_copy_160),true,() -> confirm(getString(R.string.preview_copy_161),getString(R.string.intent_proposal_confirm,item.name,date(target)),getString(R.string.preview_copy_164),() -> {s().store.moveWeatherSuggestion(item.id,target);render();notice(getString(R.string.preview_copy_165));}));
                MaterialButton keep=action(getString(R.string.intent_proposal_keep),false,() -> {s().store.dismissWeatherSuggestion(item.id);render();});
                choices.addView(change,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams keepSize=new LinearLayout.LayoutParams(0,-2,1);keepSize.leftMargin=dp(8);choices.addView(keep,keepSize);content.addView(choices);
            }
            gap(content,16);
        }
        gap(content,16);content.addView(caption(getString(R.string.preview_copy_166,ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))));
    }
    private void bindSettings() {
        LinearLayout body=box(R.id.settings_content);section(body,getString(R.string.preview_copy_168),"");
        settingsRow(body,getString(R.string.preview_copy_169),s().members==1?getString(R.string.preview_solo_house):getString(R.string.preview_house_members,s().members),R.drawable.ic_home,() -> go("family"));line(body);
        settingsRow(body,getString(R.string.preview_copy_172),s().region+(s().owner?"":getString(R.string.preview_copy_173)),R.drawable.ic_pin,() -> go("preferences","region"));
        gap(body,28);section(body,getString(R.string.preview_copy_174),"");
        settingsRow(body,getString(R.string.preview_copy_175),s().notifications?s().time+" · "+s().advanceDays+getString(R.string.preview_copy_176):getString(R.string.preview_copy_177),R.drawable.ic_bell,() -> go("preferences","notifications"));line(body);
        settingsRow(body,getString(R.string.preview_copy_178),s().permissionGranted?getString(R.string.preview_copy_179):getString(R.string.preview_copy_180),R.drawable.ic_shield,() -> go("preferences","notifications"));
        gap(body,28);section(body,getString(R.string.preview_copy_181),"");
        settingsRow(body,getString(R.string.preview_copy_182),s().condition.equals("rain")?getString(R.string.preview_copy_138):s().condition.equals("clouds")?getString(R.string.preview_copy_139):getString(R.string.preview_copy_141),R.drawable.ic_cloud,() -> go("preferences","weather"));line(body);
        settingsRow(body,getString(R.string.preview_copy_183),getString(R.string.preview_copy_184),R.drawable.ic_logout,() -> confirm(getString(R.string.preview_copy_185),getString(R.string.preview_copy_186),getString(R.string.preview_copy_183),() -> {s().authFields.clear();vm.tab("login");render();}));
        gap(body,28);body.addView(caption(getString(R.string.preview_copy_187)));
    }
    private void bindFamily() {
        LinearLayout body=box(R.id.family_content);section(body,getString(R.string.preview_copy_169),s().owner?getString(R.string.preview_copy_188):getString(R.string.preview_copy_189));
        settingsRow(body,getString(R.string.preview_copy_190),s().owner?getString(R.string.preview_copy_191):getString(R.string.preview_copy_192),R.drawable.ic_home,null);line(body);
        if(s().members>1)settingsRow(body,getString(R.string.preview_copy_193),s().owner?getString(R.string.intent_member_manage):getString(R.string.preview_copy_191),R.drawable.ic_users,s().owner?() -> confirm(getString(R.string.intent_member_remove_title),getString(R.string.intent_member_remove_help),getString(R.string.intent_member_remove),() -> {s().members=1;render();notice(getString(R.string.intent_member_removed));}):null);
        body.addView(caption(getString(R.string.intent_family_limit)));
        gap(body,28);section(body,getString(R.string.preview_copy_194),s().owner?getString(R.string.preview_copy_195):getString(R.string.preview_copy_196));
        if(s().owner) {
            TextView code=text(s().invite,R.style.TextAppearance_HomeTiming_Number);code.setLetterSpacing(.12f);body.addView(code);gap(body,8);body.addView(caption(getString(R.string.preview_copy_197)));
            fullAction(body,getString(R.string.preview_copy_198),false,() -> {android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.preview_copy_199),s().invite));notice(getString(R.string.preview_copy_200));});
            fullAction(body,getString(R.string.preview_copy_201),false,() -> confirm(getString(R.string.preview_copy_202),getString(R.string.preview_copy_203),getString(R.string.preview_copy_204),() -> {s().invite=String.valueOf(100000+new java.security.SecureRandom().nextInt(900000));render();notice(getString(R.string.preview_copy_205));}));
        }
        gap(body,28);section(body,getString(R.string.preview_copy_206),"");
        settingsRow(body,getString(R.string.preview_copy_207),getString(R.string.preview_copy_208),R.drawable.ic_plus,() -> go("preferences","join"));line(body);
        settingsRow(body,getString(R.string.preview_copy_209),s().owner?getString(R.string.preview_copy_210):getString(R.string.preview_copy_211),R.drawable.ic_logout,() -> {
            if(s().owner&&s().members>1){new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.preview_copy_212)).setMessage(getString(R.string.preview_copy_213)).setPositiveButton(getString(R.string.preview_copy_214),null).show();}
            else confirm(getString(R.string.preview_copy_215),getString(R.string.preview_copy_216),getString(R.string.preview_copy_217),() -> {s().owner=true;s().joined=false;s().members=1;s().store.items.clear();vm.tab("settings");render();notice(getString(R.string.preview_copy_218));});
        });
        gap(body,20);body.addView(caption(getString(R.string.preview_copy_219)));
    }
    private void bindPreferences() {
        LinearLayout body=box(R.id.preferences_content);String kind=s().route.id;
        TextView heading=screen.findViewById(R.id.preferences_heading);
        if(kind.equals("region")) {
            heading.setText(getString(R.string.preview_copy_220));gap(body,12);body.addView(label(getString(R.string.preview_copy_221)));gap(body,24);
            if(!s().owner){section(body,getString(R.string.preview_copy_222),s().region);return;}
            for(String region:new String[]{getString(R.string.preview_copy_223),getString(R.string.preview_copy_224),getString(R.string.preview_copy_225),getString(R.string.preview_copy_226)})settingsRow(body,region,region.equals(s().region)?getString(R.string.preview_copy_018):"",R.drawable.ic_pin,() -> {s().region=region;render();notice(getString(R.string.preview_copy_227));});
        } else if(kind.equals("notifications")) {
            heading.setText(getString(R.string.preview_copy_175));gap(body,12);body.addView(label(getString(R.string.preview_copy_228)));gap(body,24);
            SwitchMaterial toggle=new SwitchMaterial(this);toggle.setText(getString(R.string.preview_copy_229));toggle.setTextSize(17);toggle.setTextColor(color(R.color.ht_text));toggle.setMinHeight(dp(60));toggle.setChecked(s().notifications);
            toggle.setOnCheckedChangeListener((button,value) -> {s().notifications=value;notice(value?getString(R.string.preview_copy_230):getString(R.string.preview_copy_231));});body.addView(toggle);line(body);
            settingsRow(body,getString(R.string.preview_copy_232),s().time,0,() -> {String[] time=s().time.split(":");new TimePickerDialog(this,(picker,h,m) -> {s().time=String.format(Locale.ROOT,"%02d:%02d",h,m);render();},Integer.parseInt(time[0]),Integer.parseInt(time[1]),true).show();});line(body);
            settingsRow(body,getString(R.string.preview_copy_233),s().advanceDays+getString(R.string.preview_copy_176),0,() -> choose(getString(R.string.preview_copy_234),new String[]{getString(R.string.preview_copy_235),getString(R.string.preview_copy_236),getString(R.string.preview_copy_237),getString(R.string.preview_copy_238)},value -> {s().advanceDays=value.equals(getString(R.string.preview_copy_235))?0:Integer.parseInt(value.substring(0,1));render();}));
            gap(body,24);section(body,getString(R.string.preview_copy_178),s().permissionGranted?getString(R.string.preview_copy_179):getString(R.string.preview_copy_180));body.addView(caption(getString(R.string.intent_permission_separate)));body.addView(caption(getString(R.string.preview_copy_239)));
        } else if(kind.equals("join")) {
            heading.setText(getString(R.string.preview_copy_207));gap(body,12);
            if(s().members!=1||!s().store.items.isEmpty()) {
                section(body,getString(R.string.intent_join_blocked),getString(R.string.intent_join_blocked_help));
                fullAction(body,getString(R.string.intent_join_family),false,() -> go("family"));return;
            }
            body.addView(label(getString(R.string.preview_copy_240)));gap(body,24);
            EditText input=new androidx.appcompat.widget.AppCompatEditText(this);input.setTextSize(28);input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setHint("000000");input.setTextColor(color(R.color.ht_text));input.setHintTextColor(color(R.color.ht_text_secondary));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});input.setBackgroundResource(R.drawable.bg_field);input.setPadding(dp(20),dp(16),dp(20),dp(16));input.setContentDescription(getString(R.string.preview_copy_241));input.setText(s().joinCode);input.addTextChangedListener(watcher(value -> s().joinCode=value));body.addView(input);
            TextView error=caption("");error.setTextColor(color(R.color.ht_error));body.addView(error);
            fullAction(body,getString(R.string.preview_copy_242),true,() -> {
                if(input.getText().toString().length()!=6){error.setText(getString(R.string.preview_copy_243));input.requestFocus();return;}
                if(!input.getText().toString().equals("123456")){error.setText(getString(R.string.preview_copy_244));return;}
                hideKeyboard();confirm(getString(R.string.preview_copy_245),getString(R.string.preview_copy_246),getString(R.string.preview_copy_247),() -> {s().owner=false;s().joined=true;s().members=2;s().route=new DesignPreviewViewModel.Route("family");render();notice(getString(R.string.preview_copy_248));});
            });gap(body,20);body.addView(caption(getString(R.string.preview_copy_249)));
        } else {
            heading.setText(getString(R.string.preview_copy_250));gap(body,12);body.addView(label(getString(R.string.preview_copy_251)));gap(body,24);section(body,getString(R.string.preview_copy_011),"");
            chips(body,new String[]{getString(R.string.preview_copy_141),getString(R.string.preview_copy_139),getString(R.string.preview_copy_138)},s().condition.equals("rain")?getString(R.string.preview_copy_138):s().condition.equals("clouds")?getString(R.string.preview_copy_139):getString(R.string.preview_copy_141),value -> {s().condition=value.equals(getString(R.string.preview_copy_138))?"rain":value.equals(getString(R.string.preview_copy_139))?"clouds":"clear";render();});
            gap(body,16);section(body,getString(R.string.preview_copy_252),"");String[] periods={getString(R.string.preview_copy_253),getString(R.string.preview_copy_254),getString(R.string.preview_copy_255),getString(R.string.preview_copy_256),getString(R.string.preview_copy_257)};String[] keys={"auto","morning","day","evening","night"};
            chips(body,periods,periods[java.util.Arrays.asList(keys).indexOf(s().weather)],value -> {s().weather=keys[java.util.Arrays.asList(periods).indexOf(value)];render();});
            fullAction(body,getString(R.string.preview_copy_258),true,() -> go("weather"));gap(body,20);body.addView(caption(getString(R.string.preview_copy_259)));
        }
    }
    private void bindGallery() {
        LinearLayout body=box(R.id.gallery_content);section(body,getString(R.string.preview_copy_260),getString(R.string.preview_copy_261));
        String[][] pages={{getString(R.string.preview_copy_262),"login",""},{getString(R.string.preview_copy_006),"signup",""},{getString(R.string.preview_copy_007),"reset",""},{getString(R.string.preview_copy_263),"home",""},{getString(R.string.preview_copy_264),"items",""},{getString(R.string.preview_copy_265),"editor",""},{getString(R.string.preview_copy_124),"editor","filter"},{getString(R.string.preview_copy_266),"detail","filter"},{getString(R.string.preview_copy_267),"calendar",""},{getString(R.string.preview_copy_268),"weather",""},{getString(R.string.preview_copy_269),"settings",""},{getString(R.string.preview_copy_012),"family",""},{getString(R.string.preview_copy_207),"preferences","join"},{getString(R.string.preview_copy_175),"preferences","notifications"},{getString(R.string.preview_copy_270),"preferences","region"}};
        for(String[] page:pages) {settingsRow(body,page[0],"",0,() -> {if(page[1].equals("editor"))edit(page[2]);else go(page[1],page[2]);});line(body);}
        gap(body,28);section(body,getString(R.string.preview_copy_271),getString(R.string.preview_copy_272));
        String[] names={getString(R.string.preview_copy_273),getString(R.string.preview_copy_274),getString(R.string.preview_copy_275),getString(R.string.preview_copy_276),getString(R.string.preview_copy_277)},keys={"normal","empty","error","loading","offline"};
        chips(body,names,names[java.util.Arrays.asList(keys).indexOf(s().scenario)],value -> {s().scenario=keys[java.util.Arrays.asList(names).indexOf(value)];render();});
        fullAction(body,getString(R.string.preview_copy_278),true,() -> go("home"));
        gap(body,24);section(body,getString(R.string.preview_copy_279),"");settingsRow(body,getString(R.string.preview_copy_280),getString(R.string.preview_copy_281),R.drawable.ic_sun,() -> go("preferences","weather"));
        SwitchMaterial owner=new SwitchMaterial(this);owner.setText(getString(R.string.preview_copy_282));owner.setMinHeight(dp(56));owner.setChecked(s().owner);owner.setTextColor(color(R.color.ht_text));owner.setOnCheckedChangeListener((button,value) -> s().owner=value);body.addView(owner);
        SwitchMaterial permission=new SwitchMaterial(this);permission.setText(getString(R.string.intent_permission_preview));permission.setMinHeight(dp(56));permission.setChecked(s().permissionGranted);permission.setTextColor(color(R.color.ht_text));permission.setOnCheckedChangeListener((button,value) -> s().permissionGranted=value);body.addView(permission);
        fullAction(body,getString(R.string.preview_copy_283),false,() -> confirm(getString(R.string.preview_copy_284),getString(R.string.preview_copy_285),getString(R.string.preview_copy_286),() -> {s().store.reset();s().expanded=false;s().sheetHidden=false;s().scenario="normal";s().filter=getString(R.string.preview_copy_051);s().weather="auto";s().condition="clear";s().selected=PreviewStore.today();s().month=YearMonth.from(s().selected);s().owner=true;s().joined=false;s().members=2;s().joinCode="";s().draft=null;s().time="09:00";s().advanceDays=3;s().invite="284619";s().authFields.clear();s().notifications=false;s().permissionGranted=false;s().region=getString(R.string.preview_copy_223);s().scrolls.clear();render();}));
        gap(body,24);body.addView(caption(getString(R.string.preview_copy_287)));
    }
    private void choose(String title,String[] values,java.util.function.Consumer<String> pick) {new MaterialAlertDialogBuilder(this).setTitle(title).setItems(values,(dialog,index) -> pick.accept(values[index])).setNegativeButton(getString(R.string.preview_copy_288),null).show();}
    private void confirm(String title,String message,String positive,Runnable run) {new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message).setNegativeButton(getString(R.string.preview_copy_288),null).setPositiveButton(positive,(dialog,which) -> run.run()).show();}
    private void notice(String message) {Snackbar.make(binding.getRoot(),message,Snackbar.LENGTH_LONG).setAnchorView(rootPage()?binding.navigationContainer:null).show();}
}
