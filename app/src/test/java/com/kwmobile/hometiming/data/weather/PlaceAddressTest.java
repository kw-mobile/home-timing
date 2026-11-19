package com.kwmobile.hometiming.data.weather;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class PlaceAddressTest {
    private final Region jung = Region.of("서울특별시 중구", 1, 1);

    @Test public void preciseCoordinateDeterminesForecastCellAndDropsStreetAndBuilding() {
        Region result = PlaceAddress.resolve("KR", "서울특별시", "중구", null, "태평로1가",
                "대한민국 서울특별시 중구 태평로1가 세종대로 110", 37.5665, 126.9780, Collections.singletonList(jung));
        assertEquals("서울특별시 중구 태평로1가", result.name);
        assertEquals(60, result.nx); assertEquals(127, result.ny);
        assertFalse(result.name.contains("110")); assertFalse(result.name.contains("세종대로"));
    }

    @Test public void localityDoesNotHideValidSubAdministrativeDistrict() {
        Region result = PlaceAddress.resolve("KR", "서울특별시", "서울시", "중구", "명동",
                null, 37.5636, 126.9850, Collections.singletonList(jung));
        assertEquals("서울특별시 중구 명동", result.name);
    }

    @Test public void legalDongInAddressLineCanSupplementStructuredFields() {
        Region result = PlaceAddress.resolve("KR", null, null, null, null,
                "대한민국 서울특별시 중구 명동 세종대로 110", 37.5636, 126.9850, Collections.singletonList(jung));
        assertEquals("서울특별시 중구 명동", result.name);
    }

    @Test public void buildingNumberOrRoadAloneCannotInventADong() {
        assertNull(PlaceAddress.resolve("KR", "서울특별시", "중구", null, "세종대로",
                "대한민국 서울특별시 중구 세종대로 110 101동", 37.5665, 126.9780, Collections.singletonList(jung)));
        assertNull(PlaceAddress.resolve("KR", "서울특별시", "중구", null, null,
                null, 37.5665, 126.9780, Collections.singletonList(jung)));
    }

    @Test public void unseparatedFullAddressCannotBecomeDuplicatedDongName() {
        assertNull(PlaceAddress.resolve("KR", "서울특별시", "중구", null, null,
                "서울특별시중구명동", 37.5665, 126.9780, Collections.singletonList(jung)));
    }

    @Test public void unsupportedCountryOrDistrictDoesNotUseNearestName() {
        assertNull(PlaceAddress.resolve("JP", "서울특별시", "중구", null, "명동", null,
                37.5665, 126.9780, Collections.singletonList(jung)));
        assertNull(PlaceAddress.resolve("KR", "서울특별시", "종로구", null, "사직동", null,
                37.5665, 126.9780, Collections.singletonList(jung)));
    }

    @Test public void historicalProvinceAndCombinedCityDistrictNormalizeToExistingCatalog() {
        Region chuncheon = Region.of("강원특별자치도 춘천시", 73, 134);
        Region result = PlaceAddress.resolve("KR", "강원도", "춘천시", null, "효자동", null,
                37.8813, 127.7298, Collections.singletonList(chuncheon));
        assertEquals("강원특별자치도 춘천시 효자동", result.name);
        Region cityGu = Region.of("경기도 고양시덕양구", 57, 128);
        result = PlaceAddress.resolve("KR", "경기도", "고양시", "덕양구", "화정동", null,
                37.6346, 126.8327, Arrays.asList(chuncheon, cityGu));
        assertEquals("경기도 고양시 덕양구 화정동", result.name);
    }

    @Test public void invalidCoordinateAndOversizedAddressAreRejectedBeforeSave() {
        assertNull(PlaceAddress.resolve("KR", "서울특별시", "중구", null, "명동", null,
                Double.NaN, 126.9780, Collections.singletonList(jung)));
        assertNull(PlaceAddress.resolve("KR", "서울특별시", "중구", null,
                "아주아주아주아주아주아주아주아주아주아주아주아주긴동", null,
                37.5665, 126.9780, Collections.singletonList(jung)));
    }
}
