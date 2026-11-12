package com.kwmobile.hometiming.ui.main;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.ui.component.CompletionDateView;
import com.kwmobile.hometiming.ui.component.CyclePresetView;
import com.kwmobile.hometiming.ui.component.OptionalItemFieldsView;
import com.kwmobile.hometiming.ui.component.WeatherProposalView;

import java.time.YearMonth;

/** Binds local fixture screens without owning lifecycle or saved state. */
final class CatalogScreens {
    private final Context context;
    private final DesignPreviewViewModel.State preview;
    private final PreviewHost host;
    private final PreviewWidgets ui;

    CatalogScreens(View root, DesignPreviewViewModel vm, PreviewHost host, PreviewWidgets ui) {
        this.context = root.getContext();
        this.preview = vm.state;
        this.host = host;
        this.ui = ui;
    }

    void bindComponents() {
        LinearLayout body = ui.box(R.id.gallery_content);
        ui.section(
                body,
                context.getString(R.string.component_catalog),
                context.getString(R.string.component_catalog_help));
        for (WeatherProposalView.State state : WeatherProposalView.State.values()) {
            ui.gap(body, 24);
            body.addView(ui.caption(state.name()));
            ui.gap(body, 8);
            WeatherProposalView proposal = new WeatherProposalView(context);
            String reason =
                    state == WeatherProposalView.State.SUGGESTED
                            ? context.getString(R.string.component_catalog_suggestion)
                            : context.getString(
                                    state == WeatherProposalView.State.KEPT
                                            ? R.string.intent_proposal_kept
                                            : state == WeatherProposalView.State.UNCHANGED
                                                    ? R.string.intent_proposal_keep_schedule
                                                    : state
                                                                    == WeatherProposalView.State
                                                                            .OUTSIDE_RANGE
                                                            ? R.string.intent_proposal_outside
                                                            : R.string.preview_copy_154);
            proposal.bind(
                    state,
                    context.getString(R.string.component_catalog_task),
                    reason,
                    context.getString(R.string.preview_copy_160),
                    false,
                    () -> host.notice(context.getString(R.string.component_catalog_event)),
                    () -> host.notice(context.getString(R.string.component_catalog_event)),
                    null);
            body.addView(proposal);
            ui.gap(body, 16);
            ui.line(body);
        }
        ui.gap(body, 24);
        ui.section(
                body,
                context.getString(R.string.component_preset_title),
                context.getString(R.string.component_preset_help));
        CyclePresetView presets = new CyclePresetView(context);
        presets.bind(
                new String[] {
                    context.getString(R.string.component_preset_brush),
                    context.getString(R.string.component_preset_filter),
                    context.getString(R.string.component_preset_sponge)
                },
                index -> host.notice(context.getString(R.string.component_catalog_event)));
        body.addView(presets);
        ui.gap(body, 24);
        DesignPreviewViewModel.Draft draft = preview.componentDraft;
        OptionalItemFieldsView optional = new OptionalItemFieldsView(context);
        optional.bind(
                draft.productName,
                draft.location,
                draft.memo,
                draft.optionalExpanded,
                value -> draft.productName = value,
                value -> draft.location = value,
                value -> draft.memo = value,
                value -> draft.optionalExpanded = value);
        body.addView(optional);
        ui.gap(body, 24);
        CompletionDateView completion = new CompletionDateView(context);
        completion.bind(
                ui.date(PreviewStore.today()),
                true,
                () -> host.notice(context.getString(R.string.component_catalog_event)));
        body.addView(completion);
    }

    void bindGallery() {
        LinearLayout body = ui.box(R.id.gallery_content);
        ui.fullAction(
                body,
                context.getString(R.string.component_catalog),
                false,
                () -> host.go("components"));
        ui.section(
                body,
                context.getString(R.string.preview_copy_260),
                context.getString(R.string.preview_copy_261));
        String[][] pages = {
            {context.getString(R.string.preview_copy_262), "login", ""},
            {context.getString(R.string.preview_copy_006), "signup", ""},
            {context.getString(R.string.preview_copy_007), "reset", ""},
            {context.getString(R.string.preview_copy_263), "home", ""},
            {context.getString(R.string.preview_copy_264), "items", ""},
            {context.getString(R.string.preview_copy_265), "editor", ""},
            {context.getString(R.string.preview_copy_124), "editor", "filter"},
            {context.getString(R.string.preview_copy_266), "detail", "filter"},
            {context.getString(R.string.preview_copy_267), "calendar", ""},
            {context.getString(R.string.preview_copy_268), "weather", ""},
            {context.getString(R.string.preview_copy_269), "settings", ""},
            {context.getString(R.string.preview_copy_012), "family", ""},
            {context.getString(R.string.preview_copy_207), "preferences", "join"},
            {context.getString(R.string.preview_copy_175), "preferences", "notifications"},
            {context.getString(R.string.preview_copy_270), "preferences", "region"}
        };
        for (String[] page : pages) {
            ui.settingsRow(
                    body,
                    page[0],
                    "",
                    0,
                    () -> {
                        if (page[1].equals("editor")) host.edit(page[2]);
                        else host.go(page[1], page[2]);
                    });
            ui.line(body);
        }
        ui.gap(body, 28);
        ui.section(
                body,
                context.getString(R.string.preview_copy_271),
                context.getString(R.string.preview_copy_272));
        String[]
                names =
                        {
                            context.getString(R.string.preview_copy_273),
                            context.getString(R.string.preview_copy_274),
                            context.getString(R.string.preview_copy_275),
                            context.getString(R.string.preview_copy_276),
                            context.getString(R.string.preview_copy_277)
                        },
                keys = {"normal", "empty", "error", "loading", "offline"};
        ui.chips(
                body,
                names,
                names[java.util.Arrays.asList(keys).indexOf(preview.scenario)],
                value -> {
                    preview.scenario = keys[java.util.Arrays.asList(names).indexOf(value)];
                    host.render();
                });
        ui.fullAction(
                body, context.getString(R.string.preview_copy_278), true, () -> host.go("home"));
        ui.gap(body, 24);
        ui.section(body, context.getString(R.string.preview_copy_279), "");
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_280),
                context.getString(R.string.preview_copy_281),
                R.drawable.ic_sun,
                () -> host.go("preferences", "weather"));
        SwitchMaterial owner = new SwitchMaterial(context);
        owner.setText(context.getString(R.string.preview_copy_282));
        owner.setMinHeight(ui.dp(56));
        owner.setChecked(preview.owner);
        owner.setTextColor(ui.color(R.color.ht_text));
        owner.setOnCheckedChangeListener((button, value) -> preview.owner = value);
        body.addView(owner);
        SwitchMaterial permission = new SwitchMaterial(context);
        permission.setText(context.getString(R.string.intent_permission_preview));
        permission.setMinHeight(ui.dp(56));
        permission.setChecked(preview.permissionGranted);
        permission.setTextColor(ui.color(R.color.ht_text));
        permission.setOnCheckedChangeListener((button, value) -> preview.permissionGranted = value);
        body.addView(permission);
        ui.fullAction(
                body,
                context.getString(R.string.preview_copy_283),
                false,
                () ->
                        ui.confirm(
                                context.getString(R.string.preview_copy_284),
                                context.getString(R.string.preview_copy_285),
                                context.getString(R.string.preview_copy_286),
                                () -> {
                                    preview.store.reset();
                                    preview.completionDates.clear();
                                    preview.expanded = false;
                                    preview.sheetHidden = false;
                                    preview.scenario = "normal";
                                    preview.filter = context.getString(R.string.preview_copy_051);
                                    preview.weather = "auto";
                                    preview.condition = "clear";
                                    preview.selected = PreviewStore.today();
                                    preview.month = YearMonth.from(preview.selected);
                                    preview.owner = true;
                                    preview.joined = false;
                                    preview.members = 2;
                                    preview.joinCode = "";
                                    preview.draft = null;
                                    preview.time = "09:00";
                                    preview.advanceDays = 3;
                                    preview.invite = "284619";
                                    preview.authFields.clear();
                                    preview.notifications = false;
                                    preview.permissionGranted = false;
                                    preview.region = context.getString(R.string.preview_copy_223);
                                    preview.scrolls.clear();
                                    host.render();
                                }));
        ui.gap(body, 24);
        body.addView(ui.caption(context.getString(R.string.preview_copy_287)));
    }
}
