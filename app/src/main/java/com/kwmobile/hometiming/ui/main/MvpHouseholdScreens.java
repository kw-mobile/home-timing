package com.kwmobile.hometiming.ui.main;

import android.app.TimePickerDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.weather.Region;
import com.kwmobile.hometiming.domain.Member;
import com.kwmobile.hometiming.notify.ReminderManager;
import java.util.Locale;

/** Binds the existing settings/family layouts to the active session and real server operations. */
final class MvpHouseholdScreens {
    private final Context context;
    private final View root;
    private final PreviewHost host;
    private final PreviewWidgets ui;
    private final MvpViewModel vm;

    MvpHouseholdScreens(View root, PreviewHost host, PreviewWidgets ui) {
        this.context = root.getContext(); this.root = root; this.host = host; this.ui = ui; vm = host.mvp();
    }

    void settings() {
        LinearLayout body = ui.box(R.id.settings_content);
        ui.section(body, vm.session.displayName, context.getString(R.string.mvp_shared_data));
        status(body);
        ui.settingsRow(body, context.getString(R.string.preview_copy_169),
                context.getString(R.string.preview_house_members, vm.house == null ? 0 : vm.house.memberCount),
                R.drawable.ic_users, () -> host.go("family"));
        ui.line(body);
        ui.settingsRow(body, context.getString(R.string.weather_ui_region),
                vm.house == null || !vm.house.hasRegion() ? context.getString(R.string.mvp_region_unset) : vm.house.regionName,
                R.drawable.ic_pin, () -> host.go("preferences", "region"));
        ui.line(body);
        ui.settingsRow(body, context.getString(R.string.preview_copy_175), vm.notifyTime() + " · " + vm.advanceDays(),
                R.drawable.ic_bell, () -> host.go("preferences", "notifications"));
        ui.line(body);
        ui.settingsRow(body, context.getString(R.string.weather_ui_mode),
                context.getString(host.weatherViewModel().isTestMode() ? R.string.weather_ui_source_test : R.string.weather_ui_real),
                R.drawable.ic_cloud, () -> host.go("preferences", "weather"));
        ui.line(body);
        ui.settingsRow(body, context.getString(R.string.calendar_title), context.getString(R.string.calendar_choose),
                R.drawable.ic_calendar, () -> context.startActivity(new android.content.Intent(context,
                        com.kwmobile.hometiming.ui.calendar.CalendarSettingsActivity.class)));
        ui.gap(body, 24);
        ui.fullAction(body, context.getString(R.string.preview_copy_183), false,
                () -> ui.confirm(context.getString(R.string.preview_copy_185), context.getString(R.string.mvp_logout_help),
                        context.getString(R.string.preview_copy_183), vm::signOut));
        ui.fullAction(body, context.getString(R.string.mvp_delete_account), false, () -> deleteAccount(context, host, ui));
        body.addView(ui.caption(context.getString(R.string.mvp_build_version,
                com.kwmobile.hometiming.BuildConfig.VERSION_NAME)));
    }

    private void status(LinearLayout body) {
        if (vm.busy()) body.addView(ui.label(context.getString(R.string.mvp_saving)));
        if (vm.readOnly()) {
            body.addView(ui.label(context.getString(R.string.mvp_read_only)));
            ui.fullAction(body, context.getString(R.string.mvp_retry_session), false, vm::restore);
        }
    }

    void family() {
        LinearLayout body = ui.box(R.id.family_content);
        ui.section(body, context.getString(R.string.preview_copy_169), context.getString(R.string.intent_family_limit));
        status(body);
        for (Member member : vm.members) {
            boolean canRemove = vm.house != null && vm.house.isOwner(vm.session.uid) && !member.uid.equals(vm.session.uid);
            ui.settingsRow(body, member.displayName,
                    context.getString(vm.house != null && vm.house.isOwner(member.uid) ? R.string.preview_copy_191 : R.string.preview_copy_192), R.drawable.ic_users,
                    canRemove ? () -> ui.confirm(context.getString(R.string.intent_member_remove_title),
                            context.getString(R.string.mvp_remove_member_help), context.getString(R.string.intent_member_remove),
                            () -> vm.removeMember(member.uid)) : null);
        }
        if (vm.house != null && vm.house.isOwner(vm.session.uid)) {
            ui.gap(body, 24);
            ui.section(body, context.getString(R.string.preview_copy_194), context.getString(R.string.mvp_invite_help));
            String code = vm.inviteCode();
            if (!code.isEmpty()) {
                body.addView(ui.text(code, R.style.TextAppearance_HomeTiming_Number));
                ui.fullAction(body, context.getString(R.string.preview_copy_198), false, () -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                            context.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("때맞춤 초대", code));
                    host.notice(context.getString(R.string.mvp_invite_copied));
                });
                ui.fullAction(body, context.getString(R.string.mvp_stop_invite), false, vm::stopInvite);
            }
            ui.fullAction(body, context.getString(R.string.preview_copy_201), true, vm::issueInvite);
        }
        ui.gap(body, 24);
        ui.fullAction(body, context.getString(R.string.preview_copy_207), false, () -> host.go("preferences", "join"));
        if (vm.house != null && !vm.house.isOwner(vm.session.uid)) ui.fullAction(body,
                context.getString(R.string.preview_copy_209), false,
                () -> ui.confirm(context.getString(R.string.preview_copy_215), context.getString(R.string.mvp_leave_help),
                        context.getString(R.string.preview_copy_217), vm::leave));
        body.addView(ui.caption(context.getString(R.string.mvp_house_boundary)));
    }

    void preferences(String kind) {
        LinearLayout body = ui.box(R.id.preferences_content);
        TextView heading = root.findViewById(R.id.preferences_heading);
        status(body);
        switch (kind) {
            case "region":
                heading.setText(R.string.weather_ui_region);
                body.addView(ui.label(vm.house != null && vm.house.hasRegion() ? vm.house.regionName
                        : context.getString(R.string.mvp_region_unset)));
                body.addView(ui.caption(context.getString(R.string.mvp_region_shared)));
                if (vm.house == null || !vm.house.isOwner(vm.session.uid)) break;
                ui.fullAction(body, context.getString(R.string.house_location_current), true,
                        host::useCurrentHouseLocation);
                ui.fullAction(body, context.getString(R.string.weather_ui_region_choose), false, () -> {
                    WeatherViewModel weather = host.weatherViewModel();
                    if (weather.regionNames().length == 0) { weather.refresh(true); host.notice(context.getString(R.string.weather_ui_region_loading)); return; }
                    ui.choose(context.getString(R.string.weather_ui_region_choose), weather.regionNames(), name -> {
                        Region region = weather.findRegion(name);
                        if (region != null) vm.region(region);
                    });
                });
                break;
            case "notifications":
                heading.setText(R.string.preview_copy_175);
                ReminderManager reminders = ReminderManager.getInstance(context);
                body.addView(ui.label(context.getString(reminders.canShowNotifications()
                        ? R.string.mvp_permission_on : R.string.mvp_permission_off)));
                ui.fullAction(body, context.getString(R.string.mvp_request_notifications), true, host::notificationPermission);
                ui.fullAction(body, context.getString(R.string.mvp_notification_settings), false, host::notificationSettings);
                body.addView(ui.caption(context.getString(reminders.canScheduleExactAlarms()
                        ? R.string.mvp_exact_allowed : R.string.mvp_exact_late)));
                if (!reminders.canScheduleExactAlarms()) ui.fullAction(body,
                        context.getString(R.string.mvp_exact_settings), false, host::exactAlarmSettings);
                ui.settingsRow(body, context.getString(R.string.preview_copy_232), vm.notifyTime(), 0, () -> {
                    String[] parts = vm.notifyTime().split(":");
                    new TimePickerDialog(context, (picker, h, m) -> vm.settings(
                            String.format(Locale.ROOT, "%02d:%02d", h, m), vm.advanceDays()),
                            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), true).show();
                });
                ui.settingsRow(body, context.getString(R.string.preview_copy_233), String.valueOf(vm.advanceDays()), 0,
                        () -> ui.choose(context.getString(R.string.preview_copy_233), new String[]{"0", "1", "3", "7"},
                                days -> vm.settings(vm.notifyTime(), Integer.parseInt(days))));
                body.addView(ui.caption(context.getString(R.string.mvp_notifications_help)));
                break;
            case "join":
                heading.setText(R.string.preview_copy_207);
                body.addView(ui.caption(context.getString(R.string.mvp_join_help)));
                EditText input = new androidx.appcompat.widget.AppCompatEditText(context);
                input.setHint(R.string.preview_copy_241);
                input.setContentDescription(context.getString(R.string.preview_copy_241));
                input.setMinHeight(ui.dp(48)); input.setTextSize(24);
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
                input.setText(vm.joinCode());
                input.addTextChangedListener(ui.watcher(vm::joinCode));
                body.addView(input);
                ui.fullAction(body, context.getString(R.string.preview_copy_242), true, () -> {
                    String code = input.getText().toString().trim();
                    if (!code.matches("[0-9]{6}")) { input.setError(context.getString(R.string.preview_copy_243)); return; }
                    ui.confirm(context.getString(R.string.preview_copy_245), context.getString(R.string.mvp_house_boundary),
                            context.getString(R.string.preview_copy_247), () -> vm.join(code));
                });
                break;
            default:
                heading.setText(R.string.weather_ui_mode);
                body.addView(ui.caption(context.getString(host.weatherViewModel().isTestMode()
                        ? R.string.weather_ui_source_test : R.string.weather_ui_real)));
                ui.fullAction(body,
                        context.getString(R.string.mvp_toggle_test_weather), false,
                        () -> host.weatherViewModel().setTestMode(!host.weatherViewModel().isTestMode()));
                ui.fullAction(body, context.getString(R.string.weather_ui_retry), true,
                        () -> host.weatherViewModel().refresh(true));
        }
    }

    static void deleteAccount(Context context, PreviewHost host, PreviewWidgets ui) {
        EditText password = new androidx.appcompat.widget.AppCompatEditText(context);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setHint(R.string.design_037);
        password.setContentDescription(context.getString(R.string.design_037));
        password.setMinHeight(ui.dp(48));
        LinearLayout body = ui.vertical(); body.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        body.addView(ui.caption(context.getString(R.string.mvp_delete_help))); body.addView(password);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.mvp_delete_account).setView(body)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mvp_delete_account, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(-1).setOnClickListener(button -> {
            if (password.length() == 0) { password.setError(context.getString(R.string.mvp_password_required)); return; }
            host.mvp().deleteAccount(password.getText().toString()); password.setText(""); dialog.dismiss();
        }));
        dialog.show();
    }
}
