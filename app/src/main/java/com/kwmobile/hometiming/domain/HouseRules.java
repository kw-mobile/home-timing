package com.kwmobile.hometiming.domain;

import java.util.Objects;

/**
 * Who may join, leave, remove and set the region (docs/MVP.md 데이터·권한 계약, docs/DATA_MODEL.md
 * 가족 공유). Screens use these to explain a refusal before asking the server; the security rules
 * enforce the same conditions.
 */
public final class HouseRules {
    /** Why the user cannot join another house from their current one. */
    public enum JoinBlock {
        NONE,
        /** In someone else's house: leave first to get a one-person house. */
        NOT_OWNER_OF_CURRENT,
        /** Own house has other members, items or chores: only an empty one-person house can be left by joining. */
        CURRENT_HOUSE_NOT_EMPTY
    }

    /** KMA short-range forecast grid bounds. */
    public static final int NX_MAX = 149;
    public static final int NY_MAX = 253;
    public static final int REGION_NAME_MAX_LENGTH = 30;

    private HouseRules() {
    }

    public static JoinBlock canJoinFrom(House current, String uid) {
        Objects.requireNonNull(current, "current");
        if (!current.isOwner(uid)) {
            return JoinBlock.NOT_OWNER_OF_CURRENT;
        }
        if (!current.isEmptyOnePerson()) {
            return JoinBlock.CURRENT_HOUSE_NOT_EMPTY;
        }
        return JoinBlock.NONE;
    }

    /** The owner cannot leave while others remain (owner transfer is out of P0). */
    public static boolean canLeave(House house, String uid) {
        return !house.isOwner(uid);
    }

    public static boolean canRemove(House house, String uid, String targetUid) {
        return house.isOwner(uid) && !uid.equals(targetUid);
    }

    public static boolean isValidRegion(String regionName, int nx, int ny) {
        String name = regionName == null ? "" : regionName.trim();
        return !name.isEmpty()
                && name.codePointCount(0, name.length()) <= REGION_NAME_MAX_LENGTH
                && nx >= 1 && nx <= NX_MAX
                && ny >= 1 && ny <= NY_MAX;
    }
}
