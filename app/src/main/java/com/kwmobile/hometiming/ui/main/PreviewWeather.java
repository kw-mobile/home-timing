package com.kwmobile.hometiming.ui.main;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.data.preview.PreviewStore.Item;
import com.kwmobile.hometiming.ui.component.WeatherProposalView;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared weather hero and proposal rendering for the home and weather screens. */
final class PreviewWeather {
    private final Context context;
    private final View screen;
    private final DesignPreviewViewModel.State preview;
    private final PreviewHost host;
    private final PreviewWidgets ui;

    PreviewWeather(
            View root, DesignPreviewViewModel.State preview, PreviewHost host, PreviewWidgets ui) {
        this.context = root.getContext();
        this.screen = root;
        this.preview = preview;
        this.host = host;
        this.ui = ui;
    }

    void updateWeather() {
        if (screen == null) return;
        ImageView photo = screen.findViewById(R.id.weather_photo);
        if (photo == null) return;
        if (preview.route.screen.equals("home")) {
            TextView homeDate = screen.findViewById(R.id.home_date);
            if (homeDate != null)
                homeDate.setText(
                        PreviewStore.today()
                                .format(
                                        DateTimeFormatter.ofPattern(
                                                context.getString(R.string.preview_copy_034),
                                                Locale.KOREAN)));
        }
        String period =
                preview.weather.equals("auto")
                        ? HomeTimeOfDay.at(ZonedDateTime.now()).name().toLowerCase(Locale.ROOT)
                        : preview.weather;
        int image;
        if (preview.condition.equals("rain")) image = R.drawable.weather_rain;
        else if (preview.condition.equals("clouds")) image = R.drawable.weather_clouds;
        else if (period.equals("night")) image = R.drawable.weather_night;
        else if (period.equals("evening")) image = R.drawable.weather_evening;
        else if (period.equals("morning")) image = R.drawable.weather_morning;
        else image = R.drawable.weather_day;
        photo.setImageResource(image);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (image == R.drawable.weather_night) {
            // Place the moon in the upper right, clear of centered weather labels.
            android.graphics.drawable.Drawable nightPhoto = photo.getDrawable();
            photo.post(
                    () -> {
                        if (photo.getDrawable() != nightPhoto
                                || photo.getWidth() == 0
                                || photo.getHeight() == 0) return;
                        float width = photo.getWidth(), height = photo.getHeight();
                        float sourceWidth = nightPhoto.getIntrinsicWidth(),
                                sourceHeight = nightPhoto.getIntrinsicHeight();
                        float scale = Math.max(width / sourceWidth, height / sourceHeight) * 1.65f;
                        float x =
                                Math.max(
                                        width - sourceWidth * scale,
                                        Math.min(0, width * .86f - sourceWidth * .59f * scale));
                        float y =
                                Math.max(
                                        height - sourceHeight * scale,
                                        Math.min(0, height * .14f - sourceHeight * .40f * scale));
                        android.graphics.Matrix crop = new android.graphics.Matrix();
                        crop.setScale(scale, scale);
                        crop.postTranslate(x, y);
                        photo.setScaleType(ImageView.ScaleType.MATRIX);
                        photo.setImageMatrix(crop);
                    });
        }
        View hero = (View) photo.getParent();
        hero.setClipToOutline(false);
        hero.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        ((ViewGroup) hero.getParent()).setClipToPadding(false);
        if (hero.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams fullWidth =
                    (LinearLayout.LayoutParams) hero.getLayoutParams();
            int gutter = context.getResources().getDimensionPixelSize(R.dimen.ht_screen_gutter);
            fullWidth.leftMargin = -gutter;
            fullWidth.rightMargin = -gutter;
            if (!preview.route.screen.equals("home")) fullWidth.height = ui.dp(360);
            hero.setLayoutParams(fullWidth);
        }
        ((TextView) screen.findViewById(R.id.weather_location)).setText(preview.region);
        ((TextView) screen.findViewById(R.id.weather_temperature))
                .setText(period.equals("night") ? "12°" : "18°");
        ((TextView) screen.findViewById(R.id.weather_condition))
                .setText(
                        preview.condition.equals("rain")
                                ? context.getString(R.string.preview_copy_138)
                                : preview.condition.equals("clouds")
                                        ? context.getString(R.string.preview_copy_139)
                                        : period.equals("night")
                                                ? context.getString(R.string.preview_copy_140)
                                                : context.getString(R.string.preview_copy_141));
        String time = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        int periodLabel =
                period.equals("night")
                        ? R.string.preview_copy_257
                        : period.equals("evening")
                                ? R.string.preview_copy_256
                                : period.equals("morning")
                                        ? R.string.preview_copy_254
                                        : R.string.preview_copy_255;
        ((TextView) screen.findViewById(R.id.weather_time))
                .setText(
                        preview.weather.equals("auto")
                                ? context.getString(R.string.preview_weather_clock, time)
                                : context.getString(
                                        R.string.preview_weather_manual,
                                        context.getString(periodLabel)));
    }

    void bindProposal(LinearLayout parent, Item item, boolean details) {
        boolean available = preview.scenario.equals("normal");
        LocalDate target =
                preview.store.proposalTarget(item, preview.condition.equals("rain"), available);
        WeatherProposalView.State state;
        String reason;
        if (!available) {
            state = WeatherProposalView.State.UNAVAILABLE;
            reason = context.getString(R.string.preview_copy_154);
        } else if (item.due.isBefore(PreviewStore.today())
                || item.due.isAfter(PreviewStore.today().plusDays(2))) {
            state = WeatherProposalView.State.OUTSIDE_RANGE;
            reason = context.getString(R.string.intent_proposal_outside);
        } else if (item.proposalDismissed) {
            state = WeatherProposalView.State.KEPT;
            reason = context.getString(R.string.intent_proposal_kept);
        } else if (target == null) {
            state = WeatherProposalView.State.UNCHANGED;
            reason = context.getString(R.string.intent_proposal_keep_schedule);
        } else {
            state = WeatherProposalView.State.SUGGESTED;
            reason =
                    context.getString(
                            R.string.component_proposal_dates, ui.date(item.due), ui.date(target));
        }
        WeatherProposalView proposal = new WeatherProposalView(context);
        proposal.bind(
                state,
                context.getString(R.string.component_proposal_title, item.name),
                reason,
                context.getString(R.string.preview_copy_160),
                false,
                target == null
                        ? null
                        : () ->
                                ui.confirm(
                                        context.getString(R.string.preview_copy_161),
                                        context.getString(
                                                R.string.intent_proposal_confirm,
                                                item.name,
                                                ui.date(target)),
                                        context.getString(R.string.preview_copy_164),
                                        () -> {
                                            preview.store.moveWeatherSuggestion(item.id, target);
                                            host.render();
                                            host.notice(
                                                    context.getString(R.string.preview_copy_165));
                                        }),
                () -> {
                    preview.store.dismissWeatherSuggestion(item.id);
                    host.render();
                },
                details ? () -> host.go("weather") : null);
        parent.addView(proposal);
    }
}
