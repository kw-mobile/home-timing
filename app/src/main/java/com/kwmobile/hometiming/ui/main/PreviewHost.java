package com.kwmobile.hometiming.ui.main;

import android.view.View;

import com.kwmobile.hometiming.data.preview.PreviewStore.Item;

import java.time.LocalDate;

/** Shell operations needed by fixture screens; no Activity or binding escapes. */
interface PreviewHost {
    MvpViewModel mvp();

    void notificationPermission();

    void notificationSettings();

    void exactAlarmSettings();

    void go(String screen, String id);

    default void go(String screen) {
        go(screen, "");
    }

    void edit(String id);

    void render();

    void capture();

    void hideKeyboard();

    void notice(String message);

    void recordCompletion(Item item, LocalDate date);

    boolean isCurrent(View root, String routeKey);

    void refreshGlass();

    WeatherViewModel weatherViewModel();

    android.widget.ImageView homeWeatherBackdrop();

    void prepareHomeBackdrop(View hero);
}
