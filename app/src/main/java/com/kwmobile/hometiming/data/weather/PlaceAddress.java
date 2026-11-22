package com.kwmobile.hometiming.data.weather;

import com.kwmobile.hometiming.domain.HouseRules;
import java.util.List;

/** Keeps province/district/dong only; street, building number and postal address never get saved. */
public final class PlaceAddress {
    private PlaceAddress() {}

    public static Region resolve(String country, String admin, String locality, String subAdmin,
                                 String subLocality, String addressLine, double lat, double lon,
                                 List<Region> regions) {
        if (!"KR".equalsIgnoreCase(country)) return null;
        String address = normalize(String.valueOf(addressLine)) + "|" + normalize(admin + " " + locality)
                + "|" + normalize(admin + " " + subAdmin) + "|" + normalize(admin + " " + subLocality)
                + "|" + normalize(admin + " " + locality + " " + subAdmin);
        Region district = null;
        for (Region region : regions) {
            String name = normalize(region.name);
            if (address.contains(name) && (district == null || name.length() > normalize(district.name).length()))
                district = region;
        }
        if (district == null) return null;
        String dong = dong(subLocality);
        if (dong != null && normalize(dong).contains(normalize(district.name))) dong = null;
        if (dong == null && addressLine != null) {
            for (String token : addressLine.split("\\s+")) {
                String value = dong(token);
                if (value != null && !normalize(value).contains(normalize(district.name))) { dong = value; break; }
            }
        }
        if (dong == null) return null;
        String display = district.name.replaceAll("시(?=[가-힣]+구$)", "시 ") + " " + dong;
        Region result = RegionLocationMatcher.at(display, lat, lon);
        return result != null && HouseRules.isValidRegion(result.name, result.nx, result.ny) ? result : null;
    }

    private static String dong(String value) {
        if (value == null) return null;
        String token = value.trim();
        // Legal dong such as 태평로1가, administrative dong such as 가락본동, and 읍/면 are accepted.
        return token.matches("[가-힣][가-힣0-9·.]*[동읍면가]") ? token : null;
    }

    private static String normalize(String value) {
        return value.replace("전라북도", "전북특별자치도").replace("강원도", "강원특별자치도")
                .replace("제주도", "제주특별자치도").replaceAll("\\s+", "");
    }
}
