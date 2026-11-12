package com.kwmobile.hometiming.ui.main;

import android.app.TimePickerDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.kwmobile.hometiming.R;

import java.util.Locale;

/** Binds local fixture screens without owning lifecycle or saved state. */
final class HouseholdScreens {
    private final Context context;
    private final View screen;
    private final DesignPreviewViewModel vm;
    private final DesignPreviewViewModel.State preview;
    private final PreviewHost host;
    private final PreviewWidgets ui;

    HouseholdScreens(View root, DesignPreviewViewModel vm, PreviewHost host, PreviewWidgets ui) {
        this.context = root.getContext();
        this.screen = root;
        this.vm = vm;
        this.preview = vm.state;
        this.host = host;
        this.ui = ui;
    }

    void bindSettings() {
        LinearLayout body = ui.box(R.id.settings_content);
        ui.section(body, context.getString(R.string.preview_copy_168), "");
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_169),
                preview.members == 1
                        ? context.getString(R.string.preview_solo_house)
                        : context.getString(R.string.preview_house_members, preview.members),
                R.drawable.ic_home,
                () -> host.go("family"));
        ui.line(body);
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_172),
                preview.region
                        + (preview.owner ? "" : context.getString(R.string.preview_copy_173)),
                R.drawable.ic_pin,
                () -> host.go("preferences", "region"));
        ui.gap(body, 28);
        ui.section(body, context.getString(R.string.preview_copy_174), "");
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_175),
                preview.notifications
                        ? preview.time
                                + " · "
                                + preview.advanceDays
                                + context.getString(R.string.preview_copy_176)
                        : context.getString(R.string.preview_copy_177),
                R.drawable.ic_bell,
                () -> host.go("preferences", "notifications"));
        ui.line(body);
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_178),
                preview.permissionGranted
                        ? context.getString(R.string.preview_copy_179)
                        : context.getString(R.string.preview_copy_180),
                R.drawable.ic_shield,
                () -> host.go("preferences", "notifications"));
        ui.gap(body, 28);
        ui.section(body, context.getString(R.string.preview_copy_181), "");
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_182),
                preview.condition.equals("rain")
                        ? context.getString(R.string.preview_copy_138)
                        : preview.condition.equals("clouds")
                                ? context.getString(R.string.preview_copy_139)
                                : context.getString(R.string.preview_copy_141),
                R.drawable.ic_cloud,
                () -> host.go("preferences", "weather"));
        ui.line(body);
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_183),
                context.getString(R.string.preview_copy_184),
                R.drawable.ic_logout,
                () ->
                        ui.confirm(
                                context.getString(R.string.preview_copy_185),
                                context.getString(R.string.preview_copy_186),
                                context.getString(R.string.preview_copy_183),
                                () -> {
                                    preview.authFields.clear();
                                    vm.tab("login");
                                    host.render();
                                }));
        ui.gap(body, 28);
        body.addView(ui.caption(context.getString(R.string.preview_copy_187)));
    }

    void bindFamily() {
        LinearLayout body = ui.box(R.id.family_content);
        bindFamilyMembers(body);
        bindFamilyInvite(body);
        bindFamilyMembershipActions(body);
    }

    private void bindFamilyMembers(LinearLayout body) {
        ui.section(
                body,
                context.getString(R.string.preview_copy_169),
                preview.owner
                        ? context.getString(R.string.preview_copy_188)
                        : context.getString(R.string.preview_copy_189));
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_190),
                preview.owner
                        ? context.getString(R.string.preview_copy_191)
                        : context.getString(R.string.preview_copy_192),
                R.drawable.ic_home,
                null);
        ui.line(body);
        if (preview.members > 1)
            ui.settingsRow(
                    body,
                    context.getString(R.string.preview_copy_193),
                    preview.owner
                            ? context.getString(R.string.intent_member_manage)
                            : context.getString(R.string.preview_copy_191),
                    R.drawable.ic_users,
                    preview.owner
                            ? () ->
                                    ui.confirm(
                                            context.getString(R.string.intent_member_remove_title),
                                            context.getString(R.string.intent_member_remove_help),
                                            context.getString(R.string.intent_member_remove),
                                            () -> {
                                                preview.members = 1;
                                                host.render();
                                                host.notice(
                                                        context.getString(
                                                                R.string.intent_member_removed));
                                            })
                            : null);
        body.addView(ui.caption(context.getString(R.string.intent_family_limit)));
    }

    private void bindFamilyInvite(LinearLayout body) {
        ui.gap(body, 28);
        ui.section(
                body,
                context.getString(R.string.preview_copy_194),
                preview.owner
                        ? context.getString(R.string.preview_copy_195)
                        : context.getString(R.string.preview_copy_196));
        if (preview.owner) {
            TextView code = ui.text(preview.invite, R.style.TextAppearance_HomeTiming_Number);
            code.setLetterSpacing(.12f);
            body.addView(code);
            ui.gap(body, 8);
            body.addView(ui.caption(context.getString(R.string.preview_copy_197)));
            ui.fullAction(
                    body,
                    context.getString(R.string.preview_copy_198),
                    false,
                    () -> {
                        android.content.ClipboardManager clipboard =
                                (android.content.ClipboardManager)
                                        context.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                        context.getString(R.string.preview_copy_199),
                                        preview.invite));
                        host.notice(context.getString(R.string.preview_copy_200));
                    });
            ui.fullAction(
                    body,
                    context.getString(R.string.preview_copy_201),
                    false,
                    () ->
                            ui.confirm(
                                    context.getString(R.string.preview_copy_202),
                                    context.getString(R.string.preview_copy_203),
                                    context.getString(R.string.preview_copy_204),
                                    () -> {
                                        preview.invite =
                                                String.valueOf(
                                                        100000
                                                                + new java.security.SecureRandom()
                                                                        .nextInt(900000));
                                        host.render();
                                        host.notice(context.getString(R.string.preview_copy_205));
                                    }));
        }
    }

    private void bindFamilyMembershipActions(LinearLayout body) {
        ui.gap(body, 28);
        ui.section(body, context.getString(R.string.preview_copy_206), "");
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_207),
                context.getString(R.string.preview_copy_208),
                R.drawable.ic_plus,
                () -> host.go("preferences", "join"));
        ui.line(body);
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_209),
                preview.owner
                        ? context.getString(R.string.preview_copy_210)
                        : context.getString(R.string.preview_copy_211),
                R.drawable.ic_logout,
                () -> {
                    if (preview.owner && preview.members > 1) {
                        new MaterialAlertDialogBuilder(context)
                                .setTitle(context.getString(R.string.preview_copy_212))
                                .setMessage(context.getString(R.string.preview_copy_213))
                                .setPositiveButton(
                                        context.getString(R.string.preview_copy_214), null)
                                .show();
                    } else
                        ui.confirm(
                                context.getString(R.string.preview_copy_215),
                                context.getString(R.string.preview_copy_216),
                                context.getString(R.string.preview_copy_217),
                                () -> {
                                    preview.owner = true;
                                    preview.joined = false;
                                    preview.members = 1;
                                    preview.store.items.clear();
                                    vm.tab("settings");
                                    host.render();
                                    host.notice(context.getString(R.string.preview_copy_218));
                                });
                });
        ui.gap(body, 20);
        body.addView(ui.caption(context.getString(R.string.preview_copy_219)));
    }

    void bindPreferences() {
        LinearLayout body = ui.box(R.id.preferences_content);
        String kind = preview.route.id;
        TextView heading = screen.findViewById(R.id.preferences_heading);
        switch (kind) {
            case "region":
                bindRegionPreferences(body, heading);
                break;
            case "notifications":
                bindNotificationPreferences(body, heading);
                break;
            case "join":
                bindJoinPreferences(body, heading);
                break;
            default:
                bindWeatherPreferences(body, heading);
                break;
        }
    }

    private void bindRegionPreferences(LinearLayout body, TextView heading) {
        heading.setText(context.getString(R.string.preview_copy_220));
        ui.gap(body, 12);
        body.addView(ui.label(context.getString(R.string.preview_copy_221)));
        ui.gap(body, 24);
        if (!preview.owner) {
            ui.section(body, context.getString(R.string.preview_copy_222), preview.region);
            return;
        }
        for (String region :
                new String[] {
                    context.getString(R.string.preview_copy_223),
                    context.getString(R.string.preview_copy_224),
                    context.getString(R.string.preview_copy_225),
                    context.getString(R.string.preview_copy_226)
                })
            ui.settingsRow(
                    body,
                    region,
                    region.equals(preview.region)
                            ? context.getString(R.string.preview_copy_018)
                            : "",
                    R.drawable.ic_pin,
                    () -> {
                        preview.region = region;
                        host.render();
                        host.notice(context.getString(R.string.preview_copy_227));
                    });
    }

    private void bindNotificationPreferences(LinearLayout body, TextView heading) {
        heading.setText(context.getString(R.string.preview_copy_175));
        ui.gap(body, 12);
        body.addView(ui.label(context.getString(R.string.preview_copy_228)));
        ui.gap(body, 24);
        SwitchMaterial toggle = new SwitchMaterial(context);
        toggle.setText(context.getString(R.string.preview_copy_229));
        toggle.setTextSize(17);
        toggle.setTextColor(ui.color(R.color.ht_text));
        toggle.setMinHeight(ui.dp(60));
        toggle.setChecked(preview.notifications);
        toggle.setOnCheckedChangeListener(
                (button, value) -> {
                    preview.notifications = value;
                    host.notice(
                            value
                                    ? context.getString(R.string.preview_copy_230)
                                    : context.getString(R.string.preview_copy_231));
                });
        body.addView(toggle);
        ui.line(body);
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_232),
                preview.time,
                0,
                () -> {
                    String[] time = preview.time.split(":");
                    new TimePickerDialog(
                                    context,
                                    (picker, h, m) -> {
                                        preview.time =
                                                String.format(Locale.ROOT, "%02d:%02d", h, m);
                                        host.render();
                                    },
                                    Integer.parseInt(time[0]),
                                    Integer.parseInt(time[1]),
                                    true)
                            .show();
                });
        ui.line(body);
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_233),
                preview.advanceDays + context.getString(R.string.preview_copy_176),
                0,
                () ->
                        ui.choose(
                                context.getString(R.string.preview_copy_234),
                                new String[] {
                                    context.getString(R.string.preview_copy_235),
                                    context.getString(R.string.preview_copy_236),
                                    context.getString(R.string.preview_copy_237),
                                    context.getString(R.string.preview_copy_238)
                                },
                                value -> {
                                    preview.advanceDays =
                                            value.equals(
                                                            context.getString(
                                                                    R.string.preview_copy_235))
                                                    ? 0
                                                    : Integer.parseInt(value.substring(0, 1));
                                    host.render();
                                }));
        ui.gap(body, 24);
        ui.section(
                body,
                context.getString(R.string.preview_copy_178),
                preview.permissionGranted
                        ? context.getString(R.string.preview_copy_179)
                        : context.getString(R.string.preview_copy_180));
        body.addView(ui.caption(context.getString(R.string.intent_permission_separate)));
        body.addView(ui.caption(context.getString(R.string.preview_copy_239)));
    }

    private void bindJoinPreferences(LinearLayout body, TextView heading) {
        heading.setText(context.getString(R.string.preview_copy_207));
        ui.gap(body, 12);
        if (preview.members != 1 || !preview.store.items.isEmpty()) {
            ui.section(
                    body,
                    context.getString(R.string.intent_join_blocked),
                    context.getString(R.string.intent_join_blocked_help));
            ui.fullAction(
                    body,
                    context.getString(R.string.intent_join_family),
                    false,
                    () -> host.go("family"));
            return;
        }
        body.addView(ui.label(context.getString(R.string.preview_copy_240)));
        ui.gap(body, 24);
        EditText input = new androidx.appcompat.widget.AppCompatEditText(context);
        input.setTextSize(28);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("000000");
        input.setTextColor(ui.color(R.color.ht_text));
        input.setHintTextColor(ui.color(R.color.ht_text_secondary));
        input.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(6)});
        input.setBackgroundResource(R.drawable.bg_field);
        input.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16));
        input.setContentDescription(context.getString(R.string.preview_copy_241));
        input.setText(preview.joinCode);
        input.addTextChangedListener(ui.watcher(value -> preview.joinCode = value));
        body.addView(input);
        TextView error = ui.caption("");
        error.setTextColor(ui.color(R.color.ht_error));
        body.addView(error);
        ui.fullAction(
                body,
                context.getString(R.string.preview_copy_242),
                true,
                () -> {
                    if (input.getText().toString().length() != 6) {
                        error.setText(context.getString(R.string.preview_copy_243));
                        input.requestFocus();
                        return;
                    }
                    if (!input.getText().toString().equals("123456")) {
                        error.setText(context.getString(R.string.preview_copy_244));
                        return;
                    }
                    host.hideKeyboard();
                    ui.confirm(
                            context.getString(R.string.preview_copy_245),
                            context.getString(R.string.preview_copy_246),
                            context.getString(R.string.preview_copy_247),
                            () -> {
                                preview.owner = false;
                                preview.joined = true;
                                preview.members = 2;
                                preview.route = new DesignPreviewViewModel.Route("family");
                                host.render();
                                host.notice(context.getString(R.string.preview_copy_248));
                            });
                });
        ui.gap(body, 20);
        body.addView(ui.caption(context.getString(R.string.preview_copy_249)));
    }

    private void bindWeatherPreferences(LinearLayout body, TextView heading) {
        heading.setText(context.getString(R.string.preview_copy_250));
        ui.gap(body, 12);
        body.addView(ui.label(context.getString(R.string.preview_copy_251)));
        ui.gap(body, 24);
        ui.section(body, context.getString(R.string.preview_copy_011), "");
        ui.chips(
                body,
                new String[] {
                    context.getString(R.string.preview_copy_141),
                    context.getString(R.string.preview_copy_139),
                    context.getString(R.string.preview_copy_138)
                },
                preview.condition.equals("rain")
                        ? context.getString(R.string.preview_copy_138)
                        : preview.condition.equals("clouds")
                                ? context.getString(R.string.preview_copy_139)
                                : context.getString(R.string.preview_copy_141),
                value -> {
                    preview.condition =
                            value.equals(context.getString(R.string.preview_copy_138))
                                    ? "rain"
                                    : value.equals(context.getString(R.string.preview_copy_139))
                                            ? "clouds"
                                            : "clear";
                    host.render();
                });
        ui.gap(body, 16);
        ui.section(body, context.getString(R.string.preview_copy_252), "");
        String[] periods = {
            context.getString(R.string.preview_copy_253),
            context.getString(R.string.preview_copy_254),
            context.getString(R.string.preview_copy_255),
            context.getString(R.string.preview_copy_256),
            context.getString(R.string.preview_copy_257)
        };
        String[] keys = {"auto", "morning", "day", "evening", "night"};
        ui.chips(
                body,
                periods,
                periods[java.util.Arrays.asList(keys).indexOf(preview.weather)],
                value -> {
                    preview.weather = keys[java.util.Arrays.asList(periods).indexOf(value)];
                    host.render();
                });
        ui.fullAction(
                body, context.getString(R.string.preview_copy_258), true, () -> host.go("weather"));
        ui.gap(body, 20);
        body.addView(ui.caption(context.getString(R.string.preview_copy_259)));
    }
}
