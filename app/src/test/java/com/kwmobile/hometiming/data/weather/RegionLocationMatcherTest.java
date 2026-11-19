package com.kwmobile.hometiming.data.weather;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class RegionLocationMatcherTest {
    private final Region seoul = Region.of("서울특별시 중구", 60, 127);
    private final Region busan = Region.of("부산광역시 연제구", 98, 76);
    private final List<Region> cities = Arrays.asList(busan, seoul);

    @Test public void cityHallCoordinatesSelectTheirForecastGrids() {
        assertEquals(seoul, RegionLocationMatcher.nearby(37.5665, 126.9780, cities).get(0));
        assertEquals(busan, RegionLocationMatcher.nearby(35.1796, 129.0756, cities).get(0));
    }

    @Test public void sameGridCandidatesComeBeforeNearbyOnes() {
        Region sameCell = Region.of("서울특별시 종로구", 60, 127);
        Region nextCell = Region.of("서울특별시 용산구", 60, 126);
        assertEquals(Arrays.asList(sameCell, seoul, nextCell),
                RegionLocationMatcher.nearby(37.5665, 126.9780,
                        Arrays.asList(nextCell, seoul, busan, sameCell)));
    }

    @Test public void unknownOrInvalidLocationProvidesNoSuggestion() {
        assertTrue(RegionLocationMatcher.nearby(35.6762, 139.6503, cities).isEmpty());
        assertTrue(RegionLocationMatcher.nearby(Double.NaN, 126.978, cities).isEmpty());
        assertTrue(RegionLocationMatcher.nearby(37.5665, 126.9780, Arrays.asList(busan)).isEmpty());
    }
}
