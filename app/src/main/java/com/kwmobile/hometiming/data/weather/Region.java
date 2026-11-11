package com.kwmobile.hometiming.data.weather;

/** A 시·군·구 and its forecast grid; pass to {@code HouseRepository.setRegion}. */
public final class Region {
    public final String name;
    public final int nx;
    public final int ny;

    Region(String name, int nx, int ny) {
        this.name = name;
        this.nx = nx;
        this.ny = ny;
    }
}
