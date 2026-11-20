package com.kwmobile.hometiming.ui.main;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.data.preview.PreviewStore.Item;
import com.kwmobile.hometiming.ui.component.FluidMotion;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

final class PreviewWidgets {
    private final Context context;
    private final View screen;

    PreviewWidgets(View root) {
        screen = root;
        context = root.getContext();
    }

    int color(int id) {
        return ContextCompat.getColor(context, id);
    }

    int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    LinearLayout box(int id) {
        return screen.findViewById(id);
    }

    LinearLayout vertical() {
        LinearLayout v = new LinearLayout(context);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return v;
    }

    LinearLayout horizontal() {
        LinearLayout v = new LinearLayout(context);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return v;
    }

    TextView text(String value, int style) {
        TextView v = new com.google.android.material.textview.MaterialTextView(context);
        v.setTextAppearance(context, style);
        v.setText(value);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return v;
    }

    TextView label(String value) {
        return text(value, R.style.TextAppearance_HomeTiming_Label);
    }

    TextView caption(String value) {
        return text(value, R.style.TextAppearance_HomeTiming_Caption);
    }

    TextView description(String value) {
        return text(value, R.style.TextAppearance_HomeTiming_Supporting);
    }

    void gap(LinearLayout parent, int size) {
        View space = new View(context);
        space.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(size)));
    }

    void line(LinearLayout parent) {
        View line = new View(context);
        line.setBackgroundColor(color(R.color.ht_divider));
        line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    void section(LinearLayout parent, String title, String subtitle) {
        TextView heading = text(title, R.style.TextAppearance_HomeTiming_Title);
        ViewCompat.setAccessibilityHeading(heading, true);
        parent.addView(heading);
        if (!subtitle.isEmpty()) {
            gap(parent, 6);
            parent.addView(caption(subtitle));
        }
        gap(parent, 8);
    }

    MaterialButton action(String title, boolean primary, Runnable run) {
        MaterialButton b =
                new MaterialButton(
                        context, null, com.google.android.material.R.attr.materialButtonStyle);
        b.setText(title);
        b.setTextAppearance(context, R.style.TextAppearance_HomeTiming_BodyStrong);
        b.setAllCaps(false);
        b.setMinHeight(dp(52));
        b.setMinimumHeight(dp(52));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setCornerRadius(dp(14));
        b.setStrokeWidth(0);
        b.setElevation(0);
        b.setStateListAnimator(null);
        b.setBackgroundTintList(
                new ColorStateList(
                        new int[][] {{-android.R.attr.state_enabled}, {}},
                        new int[] {
                            color(R.color.ht_disabled),
                            color(primary ? R.color.ht_primary : R.color.ht_container)
                        }));
        b.setTextColor(
                new ColorStateList(
                        new int[][] {{-android.R.attr.state_enabled}, {}},
                        new int[] {
                            color(R.color.ht_on_disabled),
                            color(primary ? R.color.ht_on_primary : R.color.ht_text)
                        }));
        b.setOnClickListener(v -> run.run());
        FluidMotion.press(b);
        return b;
    }

    void fullAction(LinearLayout parent, String title, boolean primary, Runnable run) {
        MaterialButton b = action(title, primary, run);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        parent.addView(b, lp);
    }

    MaterialButton smallAction(String title, Runnable run) {
        MaterialButton b = action(title, false, run);
        b.setTextSize(14);
        b.setMinWidth(dp(48));
        b.setMinimumWidth(dp(48));
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));
        return b;
    }

    void chips(
            LinearLayout parent,
            String[] values,
            String selected,
            java.util.function.Consumer<String> pick) {
        chips(parent, values, selected, pick, null);
    }

    void chips(
            LinearLayout parent, String[] values, String selected,
            java.util.function.Consumer<String> pick, Runnable addCategory) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout group = horizontal();
        group.setLayoutParams(new FrameLayout.LayoutParams(-2, -2));
        for (String value : values) {
            MaterialButton b =
                    smallAction(
                            value,
                            () -> {
                                if (!value.equals(selected)) pick.accept(value);
                            });
            b.setBackgroundTintList(
                    ColorStateList.valueOf(
                            color(
                                    value.equals(selected)
                                            ? R.color.ht_text
                                            : R.color.ht_container)));
            b.setTextColor(color(value.equals(selected) ? R.color.ht_background : R.color.ht_text));
            b.setSelected(value.equals(selected));
            ViewCompat.setStateDescription(
                    b,
                    value.equals(selected)
                            ? context.getString(R.string.preview_copy_018)
                            : context.getString(R.string.preview_copy_019));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            group.addView(b, lp);
        }
        if (addCategory != null) {
            MaterialButton add = smallAction("+", addCategory);
            add.setContentDescription(context.getString(R.string.category_add_title));
            group.addView(add, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        scroll.addView(group);
        parent.addView(scroll);
        gap(parent, 12);
    }

    LinearLayout settingsRow(
            LinearLayout parent, String title, String description, int icon, Runnable run) {
        return settingsRow(parent, title, description, icon, run, false);
    }

    LinearLayout settingsRow(
            LinearLayout parent, String title, String description, int icon, Runnable run, boolean destructive) {
        LinearLayout row = horizontal();
        row.setMinimumHeight(dp(76));
        row.setPadding(0, dp(12), 0, dp(12));
        if (icon != 0) {
            ImageView image = new androidx.appcompat.widget.AppCompatImageView(context);
            image.setImageResource(icon);
            image.setImageTintList(ColorStateList.valueOf(color(destructive ? R.color.ht_error : R.color.ht_text)));
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(22), dp(22));
            lp.rightMargin = dp(16);
            row.addView(image, lp);
        }
        LinearLayout copy = vertical();
        copy.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView label = text(title, R.style.TextAppearance_HomeTiming_BodyStrong);
        if (destructive) label.setTextColor(color(R.color.ht_error));
        copy.addView(label);
        if (!description.isEmpty()) {
            gap(copy, 4);
            copy.addView(caption(description));
        }
        row.addView(copy);
        if (run != null) {
            ImageView arrow = new androidx.appcompat.widget.AppCompatImageView(context);
            arrow.setImageResource(R.drawable.ic_arrow);
            arrow.setImageTintList(ColorStateList.valueOf(color(R.color.ht_icon_secondary)));
            arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(16), dp(16)));
            android.util.TypedValue feedback = new android.util.TypedValue();
            context.getTheme()
                    .resolveAttribute(android.R.attr.selectableItemBackground, feedback, true);
            row.setBackgroundResource(feedback.resourceId);
            row.setFocusable(true);
            row.setOnClickListener(v -> run.run());
        }
        parent.addView(row);
        return row;
    }

    String date(LocalDate day) {
        return day.format(
                DateTimeFormatter.ofPattern(
                        context.getString(R.string.preview_copy_020), Locale.KOREAN));
    }

    String status(Item item) {
        long d = ChronoUnit.DAYS.between(PreviewStore.today(), item.due);
        return d < 0
                ? Math.abs(d) + context.getString(R.string.preview_copy_021)
                : d == 0 ? context.getString(R.string.preview_copy_017) : "D−" + d;
    }

    int statusColor(Item item) {
        long d = ChronoUnit.DAYS.between(PreviewStore.today(), item.due);
        return d <= 0 ? R.color.ht_error : d <= 3 ? R.color.ht_due_soon : R.color.ht_text_secondary;
    }

    int icon(Item item) {
        return item.chore
                ? (item.category.equals(context.getString(R.string.preview_copy_092))
                        ? R.drawable.ic_clean
                        : R.drawable.ic_wash)
                : item.name.contains(context.getString(R.string.preview_copy_022))
                        ? R.drawable.ic_brush
                        : item.category.equals(context.getString(R.string.preview_copy_023))
                                ? R.drawable.ic_filter
                                : R.drawable.ic_box;
    }

    void fieldError(int id, String message) {
        TextView view = screen.findViewById(id);
        if (view != null) {
            view.setText(message);
            view.setVisibility(View.VISIBLE);
            ViewCompat.setAccessibilityLiveRegion(
                    view, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
        }
    }

    void clearError(int id) {
        View view = screen.findViewById(id);
        if (view != null) view.setVisibility(View.GONE);
    }

    void focusFirstError() {
        int[][] fields = {
            {R.id.auth_name_error, R.id.auth_name},
            {R.id.auth_email_error, R.id.auth_email},
            {R.id.auth_password_error, R.id.auth_password},
            {R.id.auth_confirm_error, R.id.auth_confirm},
            {R.id.edit_name_error, R.id.edit_name},
            {R.id.edit_cycle_error, R.id.edit_cycle}
        };
        for (int[] pair : fields) {
            View error = screen.findViewById(pair[0]);
            View input = screen.findViewById(pair[1]);
            if (error != null && input != null && error.getVisibility() == View.VISIBLE) {
                input.requestFocus();
                input.announceForAccessibility(((TextView) error).getText());
                return;
            }
        }
    }

    TextWatcher watcher(java.util.function.Consumer<String> changed) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence v, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence v, int a, int b, int c) {
                changed.accept(v.toString());
            }

            @Override
            public void afterTextChanged(Editable value) {}
        };
    }

    void choose(String title, String[] values, java.util.function.Consumer<String> pick) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setItems(values, (dialog, index) -> pick.accept(values[index]))
                .setNegativeButton(context.getString(R.string.preview_copy_288), null)
                .show();
    }

    void confirm(String title, String message, String positive, Runnable run) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(context.getString(R.string.preview_copy_288), null)
                .setPositiveButton(positive, (dialog, which) -> run.run())
                .show();
    }

    boolean stateMessage(
            LinearLayout parent,
            DesignPreviewViewModel.State preview,
            Runnable retry,
            Runnable addItem) {
        String state = preview.scenario;
        if (state.equals("normal")) return false;
        if (state.equals("offline")) {
            parent.addView(caption(context.getString(R.string.mvp_offline)));
            gap(parent, 16);
            return false;
        }
        gap(parent, 24);
        if (state.equals("empty") && preview.route.screen.equals("weather")) {
            section(
                    parent,
                    context.getString(R.string.preview_forecast_empty),
                    context.getString(R.string.preview_forecast_empty_help));
            fullAction(
                    parent,
                    context.getString(R.string.preview_copy_031),
                    true,
                    () -> {
                        preview.scenario = "normal";
                        retry.run();
                    });
            return true;
        }
        if (state.equals("loading")) {
            android.widget.ProgressBar progress = new android.widget.ProgressBar(context);
            parent.addView(progress, new LinearLayout.LayoutParams(dp(28), dp(28)));
            gap(parent, 16);
            parent.addView(label(context.getString(R.string.preview_copy_027)));
            fullAction(
                    parent,
                    context.getString(R.string.preview_copy_028),
                    false,
                    () -> {
                        preview.scenario = "normal";
                        retry.run();
                    });
        } else if (state.equals("error")) {
            section(
                    parent,
                    context.getString(R.string.preview_copy_029),
                    context.getString(R.string.preview_copy_030));
            fullAction(
                    parent,
                    context.getString(R.string.preview_copy_031),
                    true,
                    () -> {
                        preview.scenario = "normal";
                        retry.run();
                    });
        } else {
            section(
                    parent,
                    context.getString(R.string.preview_copy_032),
                    context.getString(R.string.preview_copy_033));
            fullAction(
                    parent,
                    context.getString(R.string.preview_copy_008),
                    true,
                    () -> addItem.run());
        }
        return true;
    }
}
