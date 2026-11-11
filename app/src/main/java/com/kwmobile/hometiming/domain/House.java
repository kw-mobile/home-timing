package com.kwmobile.hometiming.domain;

import java.util.Objects;

/** A house as its members see it (docs/DATA_MODEL.md houses). Region and invite may be unset. */
public final class House {
    public static final int MAX_MEMBERS = 6;

    public final String id;
    public final String ownerId;
    public final int memberCount;
    public final int itemCount;
    public final int choreCount;
    /** Null until the owner sets a region. */
    public final String regionName;
    public final Integer nx;
    public final Integer ny;
    /** Null when no invite is open. */
    public final String activeInviteCode;

    public House(String id, String ownerId, int memberCount, int itemCount, int choreCount,
                 String regionName, Integer nx, Integer ny, String activeInviteCode) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.memberCount = memberCount;
        this.itemCount = itemCount;
        this.choreCount = choreCount;
        this.regionName = regionName;
        this.nx = nx;
        this.ny = ny;
        this.activeInviteCode = activeInviteCode;
    }

    public boolean isOwner(String uid) {
        return ownerId.equals(uid);
    }

    public boolean hasRoom() {
        return memberCount < MAX_MEMBERS;
    }

    public boolean hasRegion() {
        return regionName != null && nx != null && ny != null;
    }

    /** One person, no items or chores: the only kind of house its owner may leave by joining. */
    public boolean isEmptyOnePerson() {
        return memberCount == 1 && itemCount == 0 && choreCount == 0;
    }
}
