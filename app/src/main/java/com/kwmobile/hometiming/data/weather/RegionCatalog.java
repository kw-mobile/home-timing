package com.kwmobile.hometiming.data.weather;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The 시·군·구 → grid table in assets/regions.json (from KMA's official grid sheet). */
public final class RegionCatalog {
    private RegionCatalog() {
    }

    static List<Region> parse(String json) {
        List<Region> regions = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("regions")) {
            JsonObject region = element.getAsJsonObject();
            regions.add(new Region(region.get("name").getAsString(), region.get("nx").getAsInt(),
                    region.get("ny").getAsInt()));
        }
        return Collections.unmodifiableList(regions);
    }

    /** Regions whose name contains the query, ignoring spaces. An empty query returns all. */
    public static List<Region> search(List<Region> regions, String query) {
        String wanted = compact(query);
        if (wanted.isEmpty()) {
            return regions;
        }
        List<Region> matches = new ArrayList<>();
        for (Region region : regions) {
            if (compact(region.name).contains(wanted)) {
                matches.add(region);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }
}
