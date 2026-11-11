package com.kwmobile.hometiming.data.firestore;

import com.kwmobile.hometiming.domain.House;
import com.kwmobile.hometiming.domain.Member;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Converts houses, members and invites between domain models and Firestore fields
 * (docs/DATA_MODEL.md). Pure Java; the repository passes {@code FieldValue.serverTimestamp()} as
 * {@code now} and converts timestamps itself.
 */
public final class HouseDocuments {
    private HouseDocuments() {
    }

    /** Reads a stored house; null when it does not have the expected shape. */
    public static House toHouse(String id, Map<String, Object> data) {
        try {
            return new House(id,
                    text(data, Schema.Houses.OWNER_ID),
                    whole(data, Schema.Houses.MEMBER_COUNT),
                    whole(data, Schema.Houses.ITEM_COUNT),
                    whole(data, Schema.Houses.CHORE_COUNT),
                    optionalText(data, Schema.Houses.REGION_NAME),
                    optionalWhole(data, Schema.Houses.NX),
                    optionalWhole(data, Schema.Houses.NY),
                    optionalText(data, Schema.Houses.ACTIVE_INVITE_CODE));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Reads a stored member; null when malformed. {@code ownerId} marks the owner. */
    public static Member toMember(String uid, Map<String, Object> data, String ownerId) {
        try {
            return new Member(uid, text(data, Schema.Members.DISPLAY_NAME), uid.equals(ownerId));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Invite document; its expiry is createdAt + 24 h, so no expiry field is written. */
    public static Map<String, Object> inviteFields(String houseId, String uid, Object now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Schema.Invites.HOUSE_ID, Objects.requireNonNull(houseId, "houseId"));
        fields.put(Schema.Invites.CREATED_BY, Objects.requireNonNull(uid, "uid"));
        fields.put(Schema.Invites.CREATED_AT, now);
        return fields;
    }

    /** Member document of someone joining with an invite. */
    public static Map<String, Object> joinedMemberFields(String displayName, String inviteCode, Object now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Schema.Members.DISPLAY_NAME, Objects.requireNonNull(displayName, "displayName"));
        fields.put(Schema.Members.JOINED_AT, now);
        fields.put(Schema.Members.INVITE_CODE, Objects.requireNonNull(inviteCode, "inviteCode"));
        return fields;
    }

    public static Map<String, Object> regionFields(String regionName, int nx, int ny) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Schema.Houses.REGION_NAME, regionName.trim());
        fields.put(Schema.Houses.NX, nx);
        fields.put(Schema.Houses.NY, ny);
        return fields;
    }

    private static String text(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(key);
        }
        return (String) value;
    }

    private static String optionalText(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof String ? (String) value : null;
    }

    // Firestore returns integers as Long.
    private static int whole(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(key);
        }
        return Math.toIntExact(((Number) value).longValue());
    }

    private static Integer optionalWhole(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number ? Math.toIntExact(((Number) value).longValue()) : null;
    }
}
