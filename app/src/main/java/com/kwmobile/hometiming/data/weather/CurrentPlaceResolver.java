package com.kwmobile.hometiming.data.weather;

import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Android's optional address service; never logs coordinates or saves a complete postal address. */
public final class CurrentPlaceResolver {
    public interface Lookup { Task<List<Address>> find(double latitude, double longitude); }
    // Old Android has a blocking API. A stuck geocoder cannot block weather or grow a work queue.
    private static final ThreadPoolExecutor LEGACY = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), work -> {
                Thread thread = new Thread(work, "place-address"); thread.setDaemon(true); return thread;
            });
    private final Lookup lookup;

    public CurrentPlaceResolver(Geocoder geocoder) { this((lat, lon) -> lookup(geocoder, lat, lon)); }
    public CurrentPlaceResolver(Lookup lookup) { this.lookup = lookup; }

    public Task<Region> resolve(double lat, double lon, List<Region> regions) {
        try {
            return lookup.find(lat, lon).continueWith(task -> {
                if (!task.isSuccessful()) throw new IOException("Address unavailable");
                List<Address> addresses = task.getResult();
                if (addresses != null) for (Address address : addresses) {
                    if (address == null) continue;
                    Region region = PlaceAddress.resolve(address.getCountryCode(), address.getAdminArea(),
                            address.getLocality(), address.getSubAdminArea(), address.getSubLocality(),
                            address.getMaxAddressLineIndex() >= 0 ? address.getAddressLine(0) : null, lat, lon, regions);
                    if (region != null) return region;
                }
                throw new IOException("District or dong unavailable");
            });
        } catch (RuntimeException ignored) { return Tasks.forException(new IOException("Address unavailable")); }
    }

    private static Task<List<Address>> lookup(Geocoder geocoder, double lat, double lon) {
        TaskCompletionSource<List<Address>> result = new TaskCompletionSource<>();
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(lat, lon, 3, new Geocoder.GeocodeListener() {
                    @Override public void onGeocode(List<Address> addresses) { result.trySetResult(addresses); }
                    @Override public void onError(String ignored) { result.trySetException(new IOException("Address unavailable")); }
                });
            } else {
                LEGACY.execute(() -> {
                    try { result.trySetResult(legacyLookup(geocoder, lat, lon)); }
                    catch (IOException | RuntimeException ignored) { result.trySetException(new IOException("Address unavailable")); }
                });
            }
        } catch (RuntimeException ignored) { result.trySetException(new IOException("Address unavailable")); }
        return result.getTask();
    }

    @SuppressWarnings("deprecation")
    private static List<Address> legacyLookup(Geocoder geocoder, double lat, double lon) throws IOException {
        return geocoder.getFromLocation(lat, lon, 3);
    }
}
