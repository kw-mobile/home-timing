package com.kwmobile.hometiming.ui.main;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.data.preview.PreviewStore.Item;
import com.kwmobile.hometiming.ui.component.FluidMotion;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Binds local fixture screens without owning lifecycle or saved state. */
final class OverviewScreens {
    private final Context context;
    private final DesignPreviewViewModel vm;
    private final View screen;
    private final DesignPreviewViewModel.State preview;
    private final PreviewHost host;
    private final PreviewWidgets ui;
    private final PreviewItemRows itemRows;
    private final PreviewWeather weather;

    OverviewScreens(View root, DesignPreviewViewModel vm, PreviewHost host, PreviewWidgets ui) {
        this.context = root.getContext();
        this.vm = vm;
        this.screen = root;
        this.preview = vm.state;
        this.host = host;
        this.ui = ui;
        itemRows = new PreviewItemRows(root, preview, host, ui);
        weather = new PreviewWeather(root, preview, host, ui);
    }

    void bindHome() {
        bindHomeHero();
        LinearLayout heading = bindHomeRows();
        bindHomePanel(heading);
    }

    private void bindHomeHero() {
        screen.findViewById(R.id.home_weather_heading).setVisibility(View.VISIBLE);
        View homePhoto = screen.findViewById(R.id.weather_hero);
        ViewGroup.LayoutParams photoSize = homePhoto.getLayoutParams();
        photoSize.height = ui.dp(420);
        homePhoto.setLayoutParams(photoSize);
        TextView homeTemperature = screen.findViewById(R.id.weather_temperature);
        homeTemperature.setTextSize(64);
        androidx.core.widget.TextViewCompat.setLineHeight(
                homeTemperature,
                Math.round(72 * context.getResources().getDisplayMetrics().scaledDensity));
        LinearLayout photoCopy =
                (LinearLayout) screen.findViewById(R.id.weather_location).getParent();
        photoCopy.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16));
        host.prepareHomeBackdrop(homePhoto);
        updateWeather();
        screen.findViewById(R.id.weather_hero).setOnClickListener(v -> host.go("weather"));
        screen.findViewById(R.id.weather_hero)
                .setContentDescription(context.getString(R.string.preview_copy_035));
        screen.findViewById(R.id.weather_hero).setFocusable(true);
    }

    private LinearLayout bindHomeRows() {
        LinearLayout rows = ui.box(R.id.home_rows);
        LinearLayout heading = ui.horizontal();
        LinearLayout headingCopy = ui.vertical();
        headingCopy.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView title =
                ui.text(
                        context.getString(R.string.preview_copy_036),
                        R.style.TextAppearance_HomeTiming_Title);
        headingCopy.addView(title);
        TextView count = ui.caption("");
        headingCopy.addView(count);
        heading.addView(headingCopy);
        heading.addView(
                ui.smallAction(context.getString(R.string.preview_copy_037), () -> host.edit("")));
        ViewCompat.setAccessibilityHeading(title, true);
        ui.box(R.id.home_sheet_heading).addView(heading);

        if (!ui.stateMessage(rows, preview, host::render, () -> host.edit(""))) {
            List<Item> due = new ArrayList<>(), soon = new ArrayList<>();
            for (Item item : preview.store.sorted())
                if (!item.due.isAfter(PreviewStore.today())) due.add(item);
                else if (!item.due.isAfter(PreviewStore.today().plusDays(7))) soon.add(item);
            count.setText(context.getString(R.string.preview_home_due_count, due.size()));
            if (due.isEmpty()) rows.addView(ui.label(context.getString(R.string.preview_copy_040)));
            else for (Item item : due) itemRows.bind(rows, item, false);
            if (!preview.permissionGranted) {
                ui.gap(rows, 12);
                ui.settingsRow(
                        rows,
                        context.getString(R.string.preview_copy_041),
                        context.getString(R.string.preview_copy_042),
                        R.drawable.ic_bell,
                        () -> host.go("preferences", "notifications"));
                ui.line(rows);
            }
            ui.gap(rows, 28);
            ui.section(
                    rows,
                    context.getString(R.string.preview_copy_043),
                    soon.size() + context.getString(R.string.preview_copy_044));
            for (Item item : soon) itemRows.bind(rows, item, false);
        }
        ui.gap(rows, 16);
        rows.addView(ui.caption(context.getString(R.string.preview_copy_045)));
        return heading;
    }

    private LinearLayout bindHomeSummary(BottomSheetBehavior<View> behavior) {
        LinearLayout summary = ui.box(R.id.home_task_summary);
        int todayCount = 0, lateCount = 0, weekCount = 0;
        for (Item item : preview.store.sorted()) {
            long days = ChronoUnit.DAYS.between(PreviewStore.today(), item.due);
            if (days == 0) todayCount++;
            else if (days < 0) lateCount++;
            else if (days <= 7) weekCount++;
        }
        if (preview.scenario.equals("empty")) {
            todayCount = 0;
            lateCount = 0;
            weekCount = 0;
        }
        boolean unavailable =
                preview.scenario.equals("error") || preview.scenario.equals("loading");
        LinearLayout summaryRow =
                ui.settingsRow(
                        summary,
                        context.getString(R.string.intent_home_tasks),
                        unavailable
                                ? context.getString(R.string.intent_home_loading)
                                : context.getString(
                                        R.string.intent_home_counts,
                                        todayCount,
                                        lateCount,
                                        weekCount),
                        R.drawable.ic_calendar,
                        () -> behavior.setState(BottomSheetBehavior.STATE_EXPANDED));
        summaryRow.setMinimumHeight(ui.dp(64));
        summaryRow.setPadding(0, ui.dp(8), 0, ui.dp(8));
        return summary;
    }

    private void bindHomeWeatherSuggestion() {
        LinearLayout proposal = ui.box(R.id.home_weather_suggestion);
        LinearLayout row = ui.settingsRow(proposal,
                context.getString(R.string.intent_home_weather),
                weather.weatherSummary(), R.drawable.ic_wash, () -> host.go("weather"));
        row.setMinimumHeight(ui.dp(64));
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        for (Item item : preview.store.sorted()) {
            if (weather.weatherProposalTarget(item) != null) {
                proposal.addView(ui.caption(context.getString(R.string.weather_ui_fixture_suggestions)));
                weather.bindProposal(proposal, item, false);
                break;
            }
        }
    }

    private void bindHomePanel(LinearLayout heading) {
        View sheet = screen.findViewById(R.id.preview_sheet);
        ((com.kwmobile.hometiming.ui.component.SheetShadowView)
                        screen.findViewById(R.id.home_sheet_shadow))
                .follow(sheet);
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        View handle = screen.findViewById(R.id.sheet_handle);
        LinearLayout summary = bindHomeSummary(behavior);
        bindHomeWeatherSuggestion();
        View reopen = screen.findViewById(R.id.home_reopen);
        summary.setVisibility(preview.sheetHidden ? View.GONE : View.VISIBLE);
        reopen.setVisibility(preview.sheetHidden ? View.VISIBLE : View.GONE);
        reopen.setOnClickListener(v -> behavior.setState(BottomSheetBehavior.STATE_COLLAPSED));
        FluidMotion.press(reopen);
        androidx.appcompat.widget.AppCompatImageButton close =
                new androidx.appcompat.widget.AppCompatImageButton(context);
        close.setId(View.generateViewId());
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription(context.getString(R.string.preview_sheet_close));
        android.util.TypedValue closeFeedback = new android.util.TypedValue();
        context.getTheme()
                .resolveAttribute(
                        android.R.attr.selectableItemBackgroundBorderless, closeFeedback, true);
        close.setBackgroundResource(closeFeedback.resourceId);
        close.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        close.setOnClickListener(v -> behavior.setState(BottomSheetBehavior.STATE_HIDDEN));
        FluidMotion.press(close);
        LinearLayout.LayoutParams closeSize = new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48));
        closeSize.leftMargin = ui.dp(4);
        heading.addView(close, closeSize);
        sheet.post(
                () -> {
                    if (!sheet.isAttachedToWindow()) return;
                    int h = ((View) sheet.getParent()).getHeight();
                    int previousPeek =
                            Math.min(
                                    Math.max(ui.dp(265), h - ui.dp(350)),
                                    Math.max(ui.dp(220), h - ui.dp(90)));
                    int peek =
                            Math.min(
                                    Math.max(ui.dp(180), previousPeek / 2),
                                    Math.max(ui.dp(120), h - ui.dp(48)));
                    behavior.setPeekHeight(peek, false);
                    screen.findViewById(R.id.hero_scroll).setPadding(0, 0, 0, peek + ui.dp(16));
                    behavior.setState(
                            preview.sheetHidden
                                    ? BottomSheetBehavior.STATE_HIDDEN
                                    : preview.expanded
                                            ? BottomSheetBehavior.STATE_EXPANDED
                                            : BottomSheetBehavior.STATE_COLLAPSED);
                });
        handle.setOnClickListener(
                v ->
                        behavior.setState(
                                behavior.getState() == BottomSheetBehavior.STATE_EXPANDED
                                        ? BottomSheetBehavior.STATE_COLLAPSED
                                        : BottomSheetBehavior.STATE_EXPANDED));
        behavior.addBottomSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottom, int state) {
                        if (state == BottomSheetBehavior.STATE_EXPANDED
                                || state == BottomSheetBehavior.STATE_COLLAPSED
                                || state == BottomSheetBehavior.STATE_HIDDEN) {
                            preview.expanded = state == BottomSheetBehavior.STATE_EXPANDED;
                            preview.sheetHidden = state == BottomSheetBehavior.STATE_HIDDEN;
                            reopen.setVisibility(preview.sheetHidden ? View.VISIBLE : View.GONE);
                            summary.setVisibility(preview.sheetHidden ? View.GONE : View.VISIBLE);
                        }
                        handle.setContentDescription(
                                preview.expanded
                                        ? context.getString(R.string.preview_copy_046)
                                        : context.getString(R.string.preview_copy_047));
                        ViewCompat.setStateDescription(
                                handle,
                                preview.expanded
                                        ? context.getString(R.string.preview_copy_048)
                                        : context.getString(R.string.preview_copy_049));
                        View hero = screen.findViewById(R.id.hero_scroll);
                        if (hero != null)
                            hero.setImportantForAccessibility(
                                    preview.expanded
                                            ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                                            : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                    }

                    @Override
                    public void onSlide(@NonNull View bottom, float offset) {
                        host.refreshGlass();
                    }
                });
    }

    void bindItems() {
        LinearLayout controls = ui.box(R.id.item_controls);
        LinearLayout add = ui.horizontal();
        TextView count = ui.label(context.getString(R.string.preview_copy_050));
        count.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        add.addView(count);
        add.addView(
                ui.smallAction(context.getString(R.string.preview_copy_037), () -> host.edit("")));
        controls.addView(add);
        ui.gap(controls, 16);
        CategoryDialog categories = new CategoryDialog(context, vm, ui);
        controls.addView(ui.smallAction(context.getString(R.string.category_manage),
                () -> categories.manage(false, () -> {
                    host.capture();
                    host.render();
                })));
        List<String> filters = vm.categories(false, categories.defaults(false));
        filters.add(0, context.getString(R.string.preview_copy_051));
        if (!filters.contains(preview.filter)) preview.filter = filters.get(0);
        ui.chips(
                controls,
                filters.toArray(new String[0]),
                preview.filter,
                value -> {
                    host.capture();
                    preview.filter = value;
                    host.render();
                },
                () -> categories.add(false, value -> {
                    host.capture();
                    preview.filter = value;
                    host.render();
                }));
        LinearLayout rows = ui.box(R.id.item_rows);
        if (ui.stateMessage(rows, preview, host::render, () -> host.edit(""))) return;
        int countItems = 0;
        for (Item item : preview.store.sorted())
            if (!item.chore
                    && (preview.filter.equals(context.getString(R.string.preview_copy_051))
                            || preview.filter.equals(item.category))) {
                itemRows.bind(rows, item, true);
                countItems++;
            }
        if (preview.scenario.equals("normal"))
            count.setText(
                    context.getString(R.string.preview_item_count, preview.filter, countItems));
        if (countItems == 0) {
            ui.gap(rows, 32);
            ui.section(
                    rows,
                    context.getString(R.string.preview_copy_054),
                    context.getString(R.string.preview_copy_055));
            ui.fullAction(
                    rows, context.getString(R.string.preview_copy_056), true, () -> host.edit(""));
        } else {
            ui.gap(rows, 20);
            rows.addView(ui.caption(context.getString(R.string.preview_copy_057)));
        }
    }

    void bindCalendar() {
        bindCalendarMonthNavigation();
        bindCalendarGrid();
        bindCalendarItems();
    }

    private void bindCalendarMonthNavigation() {
        LinearLayout controls = ui.box(R.id.calendar_controls);
        LinearLayout month = ui.horizontal();
        MaterialButton previous =
                ui.smallAction(
                        "‹",
                        () -> {
                            host.capture();
                            preview.month = preview.month.minusMonths(1);
                            host.render();
                        });
        previous.setContentDescription(context.getString(R.string.preview_previous_month));
        month.addView(previous);
        TextView heading =
                ui.text(
                        preview.month.format(
                                DateTimeFormatter.ofPattern(
                                        context.getString(R.string.preview_copy_058),
                                        Locale.KOREAN)),
                        R.style.TextAppearance_HomeTiming_Title);
        heading.setGravity(Gravity.CENTER);
        heading.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        month.addView(heading);
        MaterialButton next =
                ui.smallAction(
                        "›",
                        () -> {
                            host.capture();
                            preview.month = preview.month.plusMonths(1);
                            host.render();
                        });
        next.setContentDescription(context.getString(R.string.preview_next_month));
        month.addView(next);
        controls.addView(month);
    }

    private void bindCalendarGrid() {
        GridLayout grid = screen.findViewById(R.id.calendar_grid);
        String[] days = {
            context.getString(R.string.preview_copy_059),
            context.getString(R.string.preview_copy_060),
            context.getString(R.string.preview_copy_061),
            context.getString(R.string.preview_copy_062),
            context.getString(R.string.preview_copy_063),
            context.getString(R.string.preview_copy_064),
            context.getString(R.string.preview_copy_065)
        };
        for (String day : days) {
            TextView cell = ui.caption(day);
            cell.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = ui.dp(40);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            grid.addView(cell, lp);
        }
        int lead = preview.month.atDay(1).getDayOfWeek().getValue() % 7;
        for (int i = 0; i < 42; i++) {
            LocalDate day = preview.month.atDay(1).plusDays(i - lead);
            boolean current = YearMonth.from(day).equals(preview.month);
            LinearLayout cell = ui.vertical();
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, ui.dp(3), 0, ui.dp(3));
            TextView number =
                    ui.text(
                            String.valueOf(day.getDayOfMonth()),
                            R.style.TextAppearance_HomeTiming_Body);
            number.setGravity(Gravity.CENTER);
            number.setTextSize(16);
            cell.addView(number);
            boolean event = false;
            for (Item item : preview.store.items) if (item.due.equals(day)) event = true;
            TextView dot = ui.caption(event && !preview.scenario.equals("empty") ? "•" : " ");
            dot.setGravity(Gravity.CENTER);
            dot.setTextSize(12);
            cell.addView(dot);
            boolean selected = day.equals(preview.selected);
            if (selected) {
                cell.setBackgroundResource(R.drawable.bg_selected);
                number.setTextColor(ui.color(R.color.ht_background));
                dot.setTextColor(ui.color(R.color.ht_background));
            } else if (!current) {
                number.setTextColor(ui.color(R.color.ht_icon_secondary));
                number.setAlpha(.65f);
            } else if (day.equals(PreviewStore.today()))
                number.setTypeface(
                        androidx.core.content.res.ResourcesCompat.getFont(
                                context, R.font.pretendard_semibold));
            cell.setContentDescription(
                    ui.date(day)
                            + (selected ? context.getString(R.string.preview_copy_066) : "")
                            + (event ? context.getString(R.string.preview_copy_067) : ""));
            cell.setSelected(selected);
            cell.setFocusable(true);
            cell.setOnClickListener(
                    v -> {
                        host.capture();
                        preview.selected = day;
                        preview.month = YearMonth.from(day);
                        host.render();
                    });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = ui.dp(54);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            grid.addView(cell, lp);
        }
    }

    private void bindCalendarItems() {
        LinearLayout rows = ui.box(R.id.calendar_rows);
        LinearLayout dayHeading = ui.horizontal();
        TextView selected =
                ui.text(ui.date(preview.selected), R.style.TextAppearance_HomeTiming_Title);
        selected.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        dayHeading.addView(selected);
        dayHeading.addView(
                ui.smallAction(
                        context.getString(R.string.preview_copy_017),
                        () -> {
                            preview.selected = PreviewStore.today();
                            preview.month = YearMonth.from(preview.selected);
                            host.render();
                        }));
        rows.addView(dayHeading);
        ui.gap(rows, 8);
        if (ui.stateMessage(rows, preview, host::render, () -> host.edit(""))) return;
        int count = 0;
        for (Item item : preview.store.sorted())
            if (item.due.equals(preview.selected)) {
                itemRows.bind(rows, item, false);
                count++;
            }
        if (count == 0) {
            ui.gap(rows, 20);
            rows.addView(ui.label(context.getString(R.string.preview_copy_068)));
            ui.gap(rows, 6);
            rows.addView(ui.caption(context.getString(R.string.preview_copy_069)));
        }
    }

    void updateWeather() {
        weather.updateWeather();
    }

    void bindWeather() {
        weather.bindWeather();
    }
}
