package com.kwmobile.hometiming.ui.main;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.data.preview.PreviewStore.Item;
import com.kwmobile.hometiming.ui.component.WeatherProposalView;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared real forecast rendering; item changes remain local preview fixtures. */
final class PreviewWeather {
    private final Context context;
    private final View screen;
    private final DesignPreviewViewModel.State preview;
    private final PreviewHost host;
    private final PreviewWidgets ui;
    private final WeatherViewModel weatherVm;

    PreviewWeather(View root, DesignPreviewViewModel.State preview, PreviewHost host, PreviewWidgets ui) {
        this.context = root.getContext();
        this.screen = root;
        this.preview = preview;
        this.host = host;
        this.ui = ui;
        this.weatherVm = host.weatherViewModel();
    }

    void updateWeather() {
        if(screen==null)return;ImageView photo=preview.route.screen.equals("home")?host.homeWeatherBackdrop():screen.findViewById(R.id.weather_photo);if(photo==null)return;
        if(preview.route.screen.equals("home")) {
            TextView homeDate=screen.findViewById(R.id.home_date);
            if(homeDate!=null)homeDate.setText(PreviewStore.today().format(DateTimeFormatter.ofPattern(context.getString(R.string.preview_copy_034),Locale.KOREAN)));
        }
        WeatherDisplay display=weatherDisplay();
        com.kwmobile.hometiming.domain.ForecastHour current=display==null?null:display.current;
        String condition=WeatherDisplay.condition(current);
        String period=!weatherVm.isTestMode()||preview.weather.equals("auto")
                ?HomeTimeOfDay.at(ZonedDateTime.now()).name().toLowerCase(Locale.ROOT):preview.weather;
        int image;
        if(condition.equals("rain")||condition.equals("sleet")||condition.equals("shower"))image=R.drawable.weather_rain;
        else if(!condition.equals("clear"))image=R.drawable.weather_clouds;
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
        View hero=screen.findViewById(R.id.weather_hero);hero.setClipToOutline(false);hero.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        ((ViewGroup)hero.getParent()).setClipToPadding(false);
        if(hero.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams fullWidth=(LinearLayout.LayoutParams)hero.getLayoutParams();
            int gutter=context.getResources().getDimensionPixelSize(R.dimen.ht_screen_gutter);
            fullWidth.leftMargin=-gutter;fullWidth.rightMargin=-gutter;
            if(!preview.route.screen.equals("home"))fullWidth.height=ui.dp(360);
            hero.setLayoutParams(fullWidth);
        }
        ((TextView)screen.findViewById(R.id.weather_location)).setText(weatherVm.regionName());
        com.kwmobile.hometiming.domain.ForecastHour activeHour = current;
        if(activeHour == null && display != null) {
            for(WeatherDisplay.Day day : display.days.values()) {
                if(day.forecast != null) { activeHour = day.forecast; break; }
            }
        }
        ((TextView)screen.findViewById(R.id.weather_temperature)).setText(degrees(activeHour==null?null:activeHour.temperature));
        WeatherViewModel.State state=weatherVm.current();
        String activeCondition = activeHour != null ? WeatherDisplay.condition(activeHour) : condition;
        String description = activeHour != null ? context.getString(weatherConditionText(activeCondition))
                : context.getString(state == null || state.loading ? R.string.ui_weather_loading : R.string.ui_weather_unavailable);
        ((TextView)screen.findViewById(R.id.weather_condition)).setText(description);
        WeatherDisplay.Day today=display==null?null:display.days.get(PreviewStore.today());
        if(today==null&&display!=null&&!display.days.isEmpty()){
            today=display.days.values().iterator().next();
        }
        ((TextView)screen.findViewById(R.id.weather_high_low)).setText(context.getString(R.string.weather_ui_high_low,
                degrees(today==null?null:today.high),degrees(today==null?null:today.low)));
        Integer pop=today==null?null:today.pop;
        if(pop==null&&activeHour!=null)pop=activeHour.pop;
        boolean hasForecast = state != null && state.result != null;
        screen.findViewById(R.id.weather_high_low).setVisibility(hasForecast ? View.VISIBLE : View.GONE);
        screen.findViewById(R.id.weather_time).setVisibility(hasForecast ? View.VISIBLE : View.GONE);
        ((TextView)screen.findViewById(R.id.weather_time)).setText(context.getString(R.string.weather_ui_pop,percent(pop)));

    }

    WeatherDisplay weatherDisplay() { return weatherVm.display(); }

    String degrees(Double value) {
        return value==null?context.getString(R.string.weather_ui_dash):String.format(Locale.ROOT,"%.0f°",value);
    }

    String percent(Integer value) {
        return value==null?context.getString(R.string.weather_ui_dash):String.format(Locale.ROOT,"%d%%",value);
    }

    int weatherConditionText(String condition) {
        switch(condition) {
            case "clear":return R.string.weather_ui_clear;
            case "clouds":return R.string.weather_ui_clouds;
            case "overcast":return R.string.weather_ui_overcast;
            case "rain":return R.string.weather_ui_rain;
            case "sleet":return R.string.weather_ui_sleet;
            case "snow":return R.string.weather_ui_snow;
            case "shower":return R.string.weather_ui_shower;
            default:return R.string.weather_ui_unknown;
        }
    }

    java.util.Map<LocalDate,com.kwmobile.hometiming.domain.DailyForecast> weatherDays() { return weatherVm.days(); }

    String weatherSummary() {
        WeatherViewModel.State state=weatherVm.current();
        if(state==null||state.loading&&state.result==null)return context.getString(R.string.weather_ui_loading);
        if (!BuildConfig.DEBUG && (state.error == com.kwmobile.hometiming.data.weather.WeatherError.NOT_CONFIGURED
                || state.error == com.kwmobile.hometiming.data.weather.WeatherError.NOT_AUTHORIZED))
            return context.getString(R.string.ui_weather_service_unavailable);
        if(state.error!=null)return BuildConfig.DEBUG&&state.providerCode!=null
                ?context.getString(R.string.weather_ui_provider_code,context.getString(state.error.messageRes),state.providerCode)
                :context.getString(state.error.messageRes);
        if(state.result==null)return context.getString(R.string.weather_ui_unjudged);
        com.kwmobile.hometiming.domain.LaundryAdvisor.Verdict verdict=
                weatherVm.verdict(PreviewStore.today());
        switch(verdict) {
            case GOOD:return context.getString(R.string.weather_ui_good);
            case CAUTION:return context.getString(R.string.weather_ui_caution);
            case BAD:return context.getString(R.string.weather_ui_bad);
            default:return context.getString(R.string.weather_ui_unjudged);
        }
    }

    String weatherSource() {
        WeatherViewModel.State state=weatherVm.current();
        if(state==null||state.result==null)return context.getString(R.string.weather_ui_loading);
        int source;
        switch(state.result.source) {
            case TEST:source=R.string.weather_ui_source_test;break;
            case STALE_CACHE:source=R.string.weather_ui_source_stale;break;
            case CACHE:source=R.string.weather_ui_source_cache;break;
            default:source=R.string.weather_ui_source_network;
        }
        String time=java.time.Instant.ofEpochMilli(state.result.fetchedAtMillis)
                .atZone(com.kwmobile.hometiming.domain.DateRules.ZONE)
                .format(DateTimeFormatter.ofPattern("M/d HH:mm",Locale.KOREAN));
        return context.getString(R.string.weather_ui_updated,context.getString(source),time);
    }

    void bindWeather() {
        updateWeather();
        LinearLayout content=ui.box(R.id.weather_content);
        TextView intro=screen.findViewById(R.id.weather_intro);
        intro.setText(context.getString(weatherVm.isTestMode()?R.string.weather_ui_source_test:R.string.weather_ui_intro));
        WeatherViewModel.State state=weatherVm.current();
        ui.settingsRow(content,context.getString(R.string.weather_ui_region),weatherVm.regionName(),R.drawable.ic_pin,
                () -> host.go("preferences","region"));
        if(state==null||state.result==null) {
            if (state != null && state.error != null) {
                ui.gap(content, 12);
                content.addView(ui.label(weatherSummary()));
            }
            if(BuildConfig.DEBUG && state!=null&&state.error==com.kwmobile.hometiming.data.weather.WeatherError.NOT_CONFIGURED)
                content.addView(ui.caption(context.getString(R.string.weather_ui_key_help)));
            com.google.android.material.button.MaterialButton retry = ui.action(
                    context.getString(R.string.weather_ui_retry), false, () -> weatherVm.refresh(true));
            retry.setEnabled(state != null && !state.loading);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = ui.dp(12); content.addView(retry, params);
            return;
        }
        ui.fullAction(content,context.getString(R.string.weather_ui_retry),false,() -> weatherVm.refresh(true));
        content.addView(ui.caption(weatherSource()));
        WeatherDisplay display=weatherDisplay();
        if(display==null)return;
        ui.gap(content,24);ui.section(content,context.getString(R.string.weather_ui_days),context.getString(R.string.weather_ui_days_help));
        for(int i=0;i<3;i++) {
            LocalDate date=PreviewStore.today().plusDays(i);
            WeatherDisplay.Day day=display.days.get(date);
            String name=ui.date(date);
            String condition=WeatherDisplay.condition(day==null?null:day.forecast);
            String forecast=day==null||day.forecast==null?name+" · "+context.getString(R.string.weather_ui_no_forecast):
                    context.getString(R.string.weather_ui_forecast_time,name,day.forecast.hour)+" · "+context.getString(weatherConditionText(condition));
            ui.settingsRow(content,forecast,context.getString(R.string.weather_ui_high_low,
                    degrees(day==null?null:day.high),degrees(day==null?null:day.low)),
                    condition.equals("clear")?R.drawable.ic_sun:
                    condition.equals("rain")||condition.equals("shower")||condition.equals("sleet")?R.drawable.ic_rain:R.drawable.ic_cloud,null);
            ui.line(content);
        }
        ui.gap(content,24);ui.section(content,context.getString(R.string.weather_ui_laundry),context.getString(R.string.weather_ui_laundry_help));
        content.addView(ui.label(weatherSummary()));
        ui.gap(content,24);ui.section(content,context.getString(R.string.preview_copy_157),context.getString(host.mvp() == null ? R.string.weather_ui_fixture_suggestions : R.string.mvp_weather_suggestions));
        for(Item item:preview.store.sorted())if(item.chore&&item.weatherSensitive) {
            ui.settingsRow(content,item.name,context.getString(R.string.preview_copy_159,ui.date(item.due)),ui.icon(item),() -> host.go("detail",item.id));
            bindProposal(content,item,false);ui.gap(content,16);
        }
    }

    LocalDate weatherProposalTarget(Item item) {
        if(!item.chore||!item.weatherSensitive||item.proposalDismissed)return null;
        return weatherVm.proposalTarget(item.due);
    }

    void bindProposal(LinearLayout parent,Item item,boolean details) {
        WeatherViewModel.State weatherState=weatherVm.current();
        LocalDate target=weatherProposalTarget(item);
        WeatherProposalView.State state;
        String reason;
        if(weatherState==null||weatherState.result==null) {
            state=WeatherProposalView.State.UNAVAILABLE;reason=weatherSummary();
        } else if(item.due.isBefore(PreviewStore.today())||!weatherDays().containsKey(item.due)) {
            state=WeatherProposalView.State.OUTSIDE_RANGE;reason=context.getString(R.string.intent_proposal_outside);
        } else if(item.proposalDismissed) {
            state=WeatherProposalView.State.KEPT;reason=context.getString(R.string.intent_proposal_kept);
        } else if(weatherVm.verdict(item.due)
                ==com.kwmobile.hometiming.domain.LaundryAdvisor.Verdict.UNKNOWN) {
            state=WeatherProposalView.State.UNAVAILABLE;reason=context.getString(R.string.weather_ui_unjudged);
        } else if(target==null) {
            state=WeatherProposalView.State.UNCHANGED;reason=context.getString(R.string.intent_proposal_keep_schedule);
        } else {
            state=WeatherProposalView.State.SUGGESTED;
            reason=context.getString(host.mvp() == null ? R.string.weather_ui_fixture_proposal : R.string.mvp_weather_proposal,ui.date(item.due),ui.date(target));
        }
        WeatherProposalView proposal=new WeatherProposalView(context);
        proposal.bind(state,context.getString(R.string.component_proposal_title,item.name),reason,context.getString(R.string.preview_copy_160),host.mvp() != null && (host.mvp().readOnly() || host.mvp().busy()),
                target==null?null:() -> ui.confirm(context.getString(R.string.preview_copy_161),
                        context.getString(host.mvp() == null ? R.string.weather_ui_fixture_confirm : R.string.mvp_weather_confirm,item.name,ui.date(target)),context.getString(R.string.preview_copy_164),
                        () -> {if (host.mvp() != null) { host.mvp().proposal(item,target); return; } preview.store.moveWeatherSuggestion(item.id,target);host.render();host.notice(context.getString(R.string.preview_copy_165));}),
                () -> {if (host.mvp() != null) { host.mvp().proposal(item,null); return; } preview.store.dismissWeatherSuggestion(item.id);host.render();},details?() -> host.go("weather"):null);
        parent.addView(proposal);
    }
}
