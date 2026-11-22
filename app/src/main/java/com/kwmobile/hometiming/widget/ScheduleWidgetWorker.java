package com.kwmobile.hometiming.widget;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class ScheduleWidgetWorker extends Worker {
    public ScheduleWidgetWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    @NonNull @Override public Result doWork() {
        if (!ScheduleWidgetProvider.hasWidgets(getApplicationContext())) return Result.success();
        return ScheduleWidgetManager.get(getApplicationContext()).sync() ? Result.success() : Result.retry();
    }
}
