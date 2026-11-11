package com.kwmobile.hometiming.data.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.HouseRules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class RegionCatalogTest {
    private List<Region> regions;

    @Before
    public void readAsset() throws IOException {
        regions = RegionCatalog.parse(new String(Files.readAllBytes(Paths.get("src/main/assets/regions.json")),
                StandardCharsets.UTF_8));
    }

    private Region only(String query) {
        List<Region> found = RegionCatalog.search(regions, query);
        assertEquals(query, 1, found.size());
        return found.get(0);
    }

    @Test
    public void everyRegionCanBeSavedOnAHouse() {
        Set<String> names = new HashSet<>();
        for (Region region : regions) {
            assertTrue(region.name, HouseRules.isValidRegion(region.name, region.nx, region.ny));
            assertTrue(region.name, names.add(region.name));
        }
        assertEquals(256, regions.size());
    }

    @Test
    public void knownGridsMatchTheOfficialSheet() {
        Region jongno = only("서울특별시 종로구");
        assertEquals(60, jongno.nx);
        assertEquals(127, jongno.ny);
        Region sejong = only("세종특별자치시");
        assertEquals(66, sejong.nx);
        assertEquals(103, sejong.ny);
    }

    @Test
    public void searchIgnoresSpaces() {
        assertEquals("부산광역시 해운대구", only("부산 광역시해운대").name);
    }

    @Test
    public void anEmptyQueryReturnsAll() {
        assertSame(regions, RegionCatalog.search(regions, "  "));
    }

    @Test
    public void anUnknownNameFindsNothing() {
        assertTrue(RegionCatalog.search(regions, "없는지역").isEmpty());
    }
}
