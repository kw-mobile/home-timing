package com.kwmobile.hometiming.data.weather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Finds nearby entries in the bundled region table using the KMA 5 km forecast projection. */
public final class RegionLocationMatcher {
    private static final double EARTH_RADIUS_IN_CELLS = 6371.00877 / 5.0;
    private static final double STANDARD_PARALLEL_1 = Math.toRadians(30);
    private static final double STANDARD_PARALLEL_2 = Math.toRadians(60);
    private static final double CONE = Math.log(Math.cos(STANDARD_PARALLEL_1)
            / Math.cos(STANDARD_PARALLEL_2))
            / Math.log(tangent(STANDARD_PARALLEL_2) / tangent(STANDARD_PARALLEL_1));
    private static final double SCALE = Math.pow(tangent(STANDARD_PARALLEL_1), CONE)
            * Math.cos(STANDARD_PARALLEL_1) / CONE;
    private static final double ORIGIN_RADIUS = radius(Math.toRadians(38));
    private static final int MAX_CANDIDATES = 3;
    private static final int MAX_DISTANCE_CELLS = 5;

    private RegionLocationMatcher() {}

    public static List<Region> nearby(double latitude, double longitude, List<Region> regions) {
        Region grid = at("", latitude, longitude);
        if (grid == null) return Collections.emptyList();
        int x = grid.nx, y = grid.ny;

        List<Region> candidates = new ArrayList<>();
        for (Region region : regions) {
            if (squaredDistance(x, y, region) <= MAX_DISTANCE_CELLS * MAX_DISTANCE_CELLS) {
                candidates.add(region);
            }
        }
        candidates.sort(Comparator.comparingLong((Region region) -> squaredDistance(x, y, region))
                .thenComparing(region -> region.name));
        return new ArrayList<>(candidates.subList(0, Math.min(MAX_CANDIDATES, candidates.size())));
    }

    /** Exact forecast cell for the received coordinate, independent of district office grids. */
    public static Region at(String name, double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude <= -90 || latitude >= 90 || longitude < -180 || longitude > 180) {
            return null;
        }
        double angle = Math.toRadians(longitude - 126);
        if (angle < -Math.PI) angle += 2 * Math.PI;
        double distance = radius(Math.toRadians(latitude));
        long x = Math.round(distance * Math.sin(angle * CONE) + 43);
        long y = Math.round(ORIGIN_RADIUS - distance * Math.cos(angle * CONE) + 136);
        if (x < 1 || x > 149 || y < 1 || y > 253) return null;

        return Region.of(name, (int) x, (int) y);
    }

    private static long squaredDistance(long x, long y, Region region) {
        long dx = x - region.nx;
        long dy = y - region.ny;
        return dx * dx + dy * dy;
    }

    private static double tangent(double latitude) {
        return Math.tan(Math.PI / 4 + latitude / 2);
    }

    private static double radius(double latitude) {
        return EARTH_RADIUS_IN_CELLS * SCALE / Math.pow(tangent(latitude), CONE);
    }
}
