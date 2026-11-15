package com.kwmobile.hometiming.data.calendar;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.TimeUnit;

public final class DeviceCalendarWorker extends Worker {
    public DeviceCalendarWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    @NonNull @Override public Result doWork() {
        DeviceCalendarManager manager = DeviceCalendarManager.get(getApplicationContext());
        if (!manager.enabled() || !manager.permitted()) return Result.success();
        try { Tasks.await(manager.sync(), 45, TimeUnit.SECONDS); return Result.success(); }
        catch (Exception ignored) { return Result.retry(); }
    }
}
