package com.kwmobile.hometiming.ui.main;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.data.preview.PreviewStore.Item;
import com.kwmobile.hometiming.ui.component.CompletionDateView;
import com.kwmobile.hometiming.ui.component.OptionalItemFieldsView;

import java.time.LocalDate;
import java.util.Locale;

/** Binds local fixture screens without owning lifecycle or saved state. */
final class ItemScreens {
    private final Context context;
    private final View screen;
    private final DesignPreviewViewModel vm;
    private final DesignPreviewViewModel.State preview;
    private final PreviewHost host;
    private final PreviewWidgets ui;

    ItemScreens(View root, DesignPreviewViewModel vm, PreviewHost host, PreviewWidgets ui) {
        this.context = root.getContext();
        this.screen = root;
        this.vm = vm;
        this.preview = vm.state;
        this.host = host;
        this.ui = ui;
    }

    void bindEditor() {
        if (preview.draft == null) preview.draft = new DesignPreviewViewModel.Draft();
        DesignPreviewViewModel.Draft d = preview.draft;
        bindEditorKind(d);
        bindEditorFields(d);
        bindEditorItemPresets(d);
        View photo = screen.findViewById(R.id.editor_photo_name);
        photo.setVisibility(!d.chore ? View.VISIBLE : View.GONE);
        photo.setOnClickListener(v -> host.photoName());
        bindEditorCategory(d);
        bindEditorDate(d);
        bindEditorCycle(d);
        bindEditorOptional(d);
        bindEditorSubmit(d);
    }

    private void bindEditorKind(DesignPreviewViewModel.Draft d) {
        ((TextView) screen.findViewById(R.id.editor_heading))
                .setText(
                        d.id.isEmpty()
                                ? context.getString(R.string.preview_copy_086)
                                : context.getString(R.string.preview_copy_009));
        LinearLayout kind = ui.box(R.id.editor_kind);
        if (!d.id.isEmpty()) {
            kind.addView(ui.label(context.getString(d.chore
                    ? R.string.preview_copy_088 : R.string.preview_copy_087)));
            ui.gap(kind, 4);
            kind.addView(ui.description(context.getString(R.string.ui_edit_kind_fixed)));
            return;
        }
        ui.chips(
                kind,
                new String[] {
                    context.getString(R.string.preview_copy_087),
                    context.getString(R.string.preview_copy_088)
                },
                d.chore
                        ? context.getString(R.string.preview_copy_088)
                        : context.getString(R.string.preview_copy_087),
                value -> {
                    host.capture();
                    d.chore = value.equals(context.getString(R.string.preview_copy_088));
                    d.cycle = d.chore ? "7" : "60";
                    d.cycleEdited = false;
                    CategoryDialog categories = new CategoryDialog(context, vm, ui);
                    d.category = vm.defaultCategory(d.chore, categories.defaults(d.chore),
                            context.getString(d.chore
                                    ? R.string.preview_copy_089 : R.string.preview_copy_053));
                    d.weather = d.chore;
                    d.dirty = true;
                    host.render();
                });
    }

    private void bindEditorItemPresets(DesignPreviewViewModel.Draft d) {
        LinearLayout container = ui.box(R.id.editor_item_presets);
        container.removeAllViews();
        container.setVisibility(d.chore ? View.GONE : View.VISIBLE);
        if (d.chore) return;
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout choices = ui.horizontal();
        int[] labels = {R.string.item_preset_brush, R.string.item_preset_sponge,
                R.string.item_preset_filter, R.string.item_preset_pillowcase};
        int[] categories = {R.string.preview_copy_023, R.string.preview_copy_052,
                R.string.preview_copy_023, R.string.preview_copy_053};
        int[] days = {90, 30, 60, 7};
        CategoryDialog categoryDialog = new CategoryDialog(context, vm, ui);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            MaterialButton button = ui.smallAction(context.getString(labels[i]), () -> {
                if (!vm.applyItemPreset(context.getString(labels[index]),
                        context.getString(categories[index]), days[index],
                        categoryDialog.defaults(false))) return;
                ((EditText) screen.findViewById(R.id.edit_name)).setText(d.name);
                ((EditText) screen.findViewById(R.id.edit_cycle)).setText(d.cycle);
                ui.clearError(R.id.edit_name_error);
                ui.clearError(R.id.edit_cycle_error);
                ui.box(R.id.editor_category).removeAllViews();
                bindEditorCategory(d);
            });
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
            size.setMarginEnd(ui.dp(8));
            choices.addView(button, size);
        }
        scroll.addView(choices, new android.widget.FrameLayout.LayoutParams(-2, -2));
        int savedScrollX = d.itemPresetScrollX;
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> d.itemPresetScrollX = x);
        container.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        scroll.post(() -> scroll.scrollTo(savedScrollX, 0));
        ui.gap(container, 8);
    }

    private void bindEditorFields(DesignPreviewViewModel.Draft d) {
        int[] ids = {R.id.edit_name, R.id.edit_cycle};
        String[] values = {d.name, d.cycle};
        for (int i = 0; i < ids.length; i++) {
            EditText field = screen.findViewById(ids[i]);
            field.setText(values[i]);
            final int id = ids[i];
            field.addTextChangedListener(
                    ui.watcher(
                            value -> {
                                if (id == R.id.edit_name) {
                                    d.name = value;
                                    if (!value.trim().isEmpty() && value.trim().length() <= 60)
                                        ui.clearError(R.id.edit_name_error);
                                }
                                else {
                                    d.cycle = value;
                                    d.cycleEdited = true;
                                }
                                d.dirty = true;
                                updateDraftDuePreview();
                            }));
        }
    }

    private void bindEditorCategory(DesignPreviewViewModel.Draft d) {
        CategoryDialog categories = new CategoryDialog(context, vm, ui);
        d.category = vm.defaultCategory(d.chore, categories.defaults(d.chore), d.category);
        LinearLayout category = ui.box(R.id.editor_category);
        ui.gap(category, 16);
        ui.settingsRow(
                category,
                context.getString(R.string.preview_copy_090),
                d.category,
                0,
                () ->
                        categories.choose(
                                d.chore,
                                d.category,
                                value -> {
                                    host.capture();
                                    d.category = value;
                                    d.dirty = true;
                                    host.render();
                                },
                                () -> {
                                    host.capture();
                                    host.render();
                                }));
        ui.line(category);
    }

    private void bindEditorDate(DesignPreviewViewModel.Draft d) {
        LinearLayout dates = ui.box(R.id.editor_date);
        ui.settingsRow(
                dates,
                d.chore
                        ? context.getString(R.string.preview_copy_093)
                        : context.getString(R.string.preview_copy_094),
                ui.date(d.date),
                R.drawable.ic_calendar,
                () -> {
                    DatePickerDialog dialog =
                            new DatePickerDialog(
                                    context,
                                    (picker, year, month, day) -> {
                                        host.capture();
                                        d.date = LocalDate.of(year, month + 1, day);
                                        d.dirty = true;
                                        host.render();
                                    },
                                    d.date.getYear(),
                                    d.date.getMonthValue() - 1,
                                    d.date.getDayOfMonth());
                    dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
                    dialog.show();
                });
        ui.line(dates);
        ui.gap(dates, 8);
        dates.addView(
                ui.caption(
                        context.getString(
                                d.chore
                                        ? R.string.preview_chore_start_help
                                        : R.string.preview_start_help)));
    }

    private void bindEditorCycle(DesignPreviewViewModel.Draft d) {
        if (d.chore) {
            ((TextView) screen.findViewById(R.id.cycle_label))
                    .setText(R.string.preview_chore_cycle_label);
            ((TextView) screen.findViewById(R.id.cycle_help))
                    .setText(R.string.preview_chore_cycle_help);
            EditText cycle = screen.findViewById(R.id.edit_cycle);
            cycle.setEnabled(false);
            LinearLayout chore = ui.box(R.id.editor_chore);
            ui.gap(chore, 20);
            chore.addView(ui.label(context.getString(R.string.preview_copy_095)));
            ui.gap(chore, 8);
            String[] weekdays = {
                context.getString(R.string.preview_copy_060),
                context.getString(R.string.preview_copy_061),
                context.getString(R.string.preview_copy_062),
                context.getString(R.string.preview_copy_063),
                context.getString(R.string.preview_copy_064),
                context.getString(R.string.preview_copy_065),
                context.getString(R.string.preview_copy_059)
            };
            ui.chips(
                    chore,
                    weekdays,
                    weekdays[d.weekday - 1],
                    value -> {
                        int target = java.util.Arrays.asList(weekdays).indexOf(value) + 1;
                        d.weekday = target;
                        d.dirty = true;
                        host.capture();
                        host.render();
                    });
            SwitchMaterial sensitive = new SwitchMaterial(context);
            sensitive.setText(context.getString(R.string.preview_copy_096));
            sensitive.setTextColor(ui.color(R.color.ht_text));
            sensitive.setMinHeight(ui.dp(52));
            sensitive.setChecked(d.weather);
            sensitive.setOnCheckedChangeListener(
                    (button, checked) -> {
                        d.weather = checked;
                        d.dirty = true;
                    });
            chore.addView(sensitive);
        }
    }

    private void bindEditorOptional(DesignPreviewViewModel.Draft d) {
        OptionalItemFieldsView optional = screen.findViewById(R.id.editor_optional);
        optional.bind(
                d.productName,
                d.location,
                d.memo,
                d.optionalExpanded,
                value -> {
                    d.productName = value;
                    d.dirty = true;
                },
                value -> {
                    d.location = value;
                    d.dirty = true;
                },
                value -> {
                    d.memo = value;
                    d.dirty = true;
                },
                value -> d.optionalExpanded = value);
        updateDraftDuePreview();
    }

    private void bindEditorSubmit(DesignPreviewViewModel.Draft d) {
        if (host.mvp() != null) ((TextView) screen.findViewById(R.id.editor_footer)).setText(R.string.mvp_remote_save_help);
        MaterialButton submit = screen.findViewById(R.id.edit_submit);
        submit.setText(
                d.id.isEmpty()
                        ? context.getString(R.string.preview_copy_097)
                        : context.getString(R.string.preview_copy_098));
        if (preview.scenario.equals("offline")) {
            submit.setEnabled(false);
            ui.fieldError(R.id.edit_cycle_error, context.getString(R.string.preview_copy_099));
        }
        submit.setOnClickListener(
                v -> {
                    boolean valid = true;
                    ui.clearError(R.id.edit_name_error);
                    ui.clearError(R.id.edit_cycle_error);
                    if (d.name.trim().isEmpty() || d.name.trim().length() > 60) {
                        ui.fieldError(
                                R.id.edit_name_error, context.getString(R.string.preview_copy_100));
                        valid = false;
                    }
                    int cycle = 0;
                    try {
                        cycle = Integer.parseInt(d.cycle);
                    } catch (NumberFormatException ignored) {
                    }
                    if (cycle < 1 || cycle > 730) {
                        ui.fieldError(
                                R.id.edit_cycle_error,
                                context.getString(R.string.preview_copy_101));
                        valid = false;
                    }
                    if (!valid) {
                        ui.focusFirstError();
                        return;
                    }
                    if (preview.scenario.equals("error")) {
                        host.notice(context.getString(R.string.preview_copy_102));
                        return;
                    }
                    if (host.mvp() != null) {
                        host.hideKeyboard();
                        host.mvp().saveDraft();
                        return;
                    }
                    Item item =
                            preview.store.save(
                                    d.id,
                                    d.name.trim(),
                                    d.category,
                                    d.location.trim(),
                                    d.chore,
                                    cycle,
                                    d.date,
                                    d.weather,
                                    d.weekday);
                    item.productName = d.productName.trim();
                    item.memo = d.memo.trim();
                    host.hideKeyboard();
                    preview.draft = null;
                    if (!preview.back.isEmpty() && preview.back.peek().screen.equals("detail"))
                        preview.back.pop();
                    preview.route = new DesignPreviewViewModel.Route("detail", item.id);
                    host.render();
                    host.notice(context.getString(R.string.preview_copy_103));
                });
    }

    private void updateDraftDuePreview() {
        if (screen == null || preview.draft == null) return;
        TextView duePreview = screen.findViewById(R.id.next_due_preview);
        if (duePreview == null) return;
        DesignPreviewViewModel.Draft draft = preview.draft;
        try {
            int days = Integer.parseInt(draft.cycle);
            if (days < 1 || days > 730) {
                duePreview.setText("");
                return;
            }
            LocalDate next =
                    preview.store.previewDue(
                            draft.id, draft.chore, days, draft.date, draft.weekday);
            duePreview.setText(context.getString(R.string.preview_next_due, ui.date(next)));
        } catch (NumberFormatException invalid) {
            duePreview.setText("");
        }
    }

    void bindDetail() {
        LinearLayout body = ui.box(R.id.detail_content);
        Item item = preview.store.find(preview.route.id);
        if (item == null) {
            ui.section(
                    body,
                    context.getString(R.string.preview_copy_104),
                    context.getString(R.string.preview_copy_105));
            ui.fullAction(
                    body,
                    context.getString(R.string.preview_copy_106),
                    true,
                    () -> {
                        vm.tab("items");
                        host.render();
                    });
            return;
        }
        bindDetailHeading(body, item);
        bindDetailCompletion(body, item);
        bindDetailMetadata(body, item);
        bindDetailActions(body, item);
    }

    private void bindDetailHeading(LinearLayout body, Item item) {
        body.addView(
                ui.caption(
                        (item.chore
                                        ? context.getString(R.string.preview_copy_088)
                                        : context.getString(R.string.preview_copy_087))
                                + " / "
                                + item.category));
        ui.gap(body, 12);
        TextView name = ui.text(item.name, R.style.TextAppearance_HomeTiming_Display);
        ViewCompat.setAccessibilityHeading(name, true);
        body.addView(name);
        ui.gap(body, 28);
        TextView status = ui.text(ui.status(item), R.style.TextAppearance_HomeTiming_Number);
        status.setTextColor(ui.color(ui.statusColor(item)));
        body.addView(status);
        ui.gap(body, 6);
        body.addView(ui.text(ui.date(item.due), R.style.TextAppearance_HomeTiming_Title));
        ui.gap(body, 8);
        body.addView(
                ui.caption(
                        item.chore
                                ? context.getString(R.string.preview_copy_107)
                                : context.getString(R.string.preview_copy_108)));
        if (item.chore && !item.due.equals(item.plannedDue)) {
            ui.gap(body, 4);
            body.addView(
                    ui.caption(
                            context.getString(
                                    R.string.intent_original_plan, ui.date(item.plannedDue))));
        }
        ui.gap(body, 16);
    }

    private void bindDetailCompletion(LinearLayout body, Item item) {
        boolean done = preview.store.completedToday(item);
        LocalDate selected = preview.completionDates.getOrDefault(item.id, PreviewStore.today());
        if (selected.isBefore(preview.store.earliestCompletion(item))
                || selected.isAfter(PreviewStore.today())) selected = PreviewStore.today();
        final LocalDate completionDate = selected;
        CompletionDateView dateControl = new CompletionDateView(context);
        dateControl.bind(
                ui.date(completionDate),
                !done && !preview.scenario.equals("offline"),
                () -> {
                    DatePickerDialog picker =
                            new DatePickerDialog(
                                    context,
                                    (view, year, month, day) -> {
                                        preview.completionDates.put(
                                                item.id, LocalDate.of(year, month + 1, day));
                                        host.capture();
                                        host.render();
                                    },
                                    completionDate.getYear(),
                                    completionDate.getMonthValue() - 1,
                                    completionDate.getDayOfMonth());
                    picker.getDatePicker()
                            .setMinDate(
                                    preview.store
                                            .earliestCompletion(item)
                                            .atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
                                            .toInstant()
                                            .toEpochMilli());
                    picker.getDatePicker().setMaxDate(System.currentTimeMillis());
                    picker.show();
                });
        body.addView(dateControl);
        MaterialButton complete =
                ui.action(
                        item.chore
                                ? context.getString(R.string.preview_copy_109)
                                : context.getString(R.string.preview_copy_110),
                        true,
                        () -> host.recordCompletion(item, completionDate));
        body.addView(complete, new LinearLayout.LayoutParams(-1, -2));
        if (done || preview.scenario.equals("offline")) {
            complete.setEnabled(false);
            complete.setBackgroundTintList(ColorStateList.valueOf(ui.color(R.color.ht_disabled)));
            complete.setTextColor(ui.color(R.color.ht_on_disabled));
            ui.gap(body, 8);
            body.addView(
                    ui.caption(
                            done
                                    ? context.getString(R.string.preview_copy_111)
                                    : context.getString(R.string.preview_copy_112)));
        }
    }

    private void bindDetailMetadata(LinearLayout body, Item item) {
        ui.gap(body, 32);
        ui.line(body);
        ui.gap(body, 24);
        ui.section(body, context.getString(R.string.preview_copy_113), "");
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_114),
                item.location.isEmpty()
                        ? context.getString(R.string.preview_copy_115)
                        : item.location,
                0,
                null);
        ui.settingsRow(
                body,
                context.getString(R.string.preview_copy_116),
                item.chore
                        ? context.getString(
                                R.string.preview_copy_117,
                                java.time.DayOfWeek.of(item.weekday)
                                        .getDisplayName(
                                                java.time.format.TextStyle.FULL, Locale.KOREAN))
                        : item.cycle + context.getString(R.string.preview_copy_118),
                0,
                null);
        if (!preview.live || !item.chore) ui.settingsRow(body, context.getString(R.string.preview_copy_094),
                ui.date(item.serverItem == null ? item.start : item.serverItem.startDate), 0, null);
        if (item.productName != null && !item.productName.isEmpty())
            ui.settingsRow(
                    body,
                    context.getString(R.string.component_product_name),
                    item.productName,
                    0,
                    null);
        if (item.memo != null && !item.memo.isEmpty())
            ui.settingsRow(body, context.getString(R.string.component_memo), item.memo, 0, null);
        ui.gap(body, 24);
        ui.section(
                body,
                item.chore
                        ? context.getString(R.string.preview_copy_119)
                        : context.getString(R.string.preview_copy_120),
                item.history.size() + context.getString(R.string.preview_copy_121));
        if (item.history.isEmpty()) {
            ui.gap(body, 16);
            body.addView(ui.label(context.getString(R.string.preview_copy_122)));
        } else
            for (PreviewStore.History event : item.history) {
                ui.settingsRow(
                        body,
                        ui.date(event.date),
                        event.person + context.getString(R.string.preview_copy_123),
                        R.drawable.ic_check,
                        null);
                ui.line(body);
            }
        ui.gap(body, 24);
    }

    private void bindDetailActions(LinearLayout body, Item item) {
        LinearLayout actions = ui.horizontal();
        MaterialButton edit =
                ui.action(
                        context.getString(R.string.preview_copy_124),
                        false,
                        () -> host.edit(item.id));
        MaterialButton delete =
                ui.action(
                        context.getString(R.string.preview_copy_125),
                        false,
                        () ->
                                ui.confirm(
                                        context.getString(R.string.preview_copy_126),
                                        context.getString(host.mvp() == null ? R.string.preview_copy_127 : R.string.mvp_delete_item_help),
                                        context.getString(R.string.preview_copy_125),
                                        () -> {
                                            if (host.mvp() != null) { host.mvp().delete(item); return; }
                                            preview.store.delete(item.id);
                                            vm.back();
                                            host.render();
                                            host.notice(
                                                    context.getString(R.string.preview_copy_128));
                                        }));
        delete.setTextColor(ui.color(R.color.ht_error));
        actions.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = ui.dp(12);
        actions.addView(delete, lp);
        body.addView(actions);
        if (preview.scenario.equals("offline")) {
            edit.setEnabled(false);
            delete.setEnabled(false);
        }
        if (!preview.owner && !preview.person.equals(item.createdBy)) {
            delete.setEnabled(false);
            ui.gap(body, 8);
            body.addView(ui.caption(context.getString(R.string.intent_delete_permission)));
        }
        ui.gap(body, 20);
        body.addView(ui.caption(context.getString(host.mvp() == null ? R.string.preview_copy_129 : R.string.mvp_remote_save_help)));
    }
}
