package com.kwmobile.hometiming.data.firestore;

import com.kwmobile.hometiming.domain.AccountInputValidator;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Decides and builds the documents that give a signed-in user a house (docs/DATA_MODEL.md).
 *
 * <p>Pure Java so the decision and fields are unit-tested; the repository writes the maps in one
 * transaction and passes {@code FieldValue.serverTimestamp()} as {@code now}.
 */
public final class HouseholdSetup {
    public enum Action {
        /** The user document exists and still has a member document in its current house. */
        READY,
        /** First sign-in: create the user, a one-person house and its member document. */
        CREATE_FIRST_HOUSE,
        /** The member document is gone (removed by the owner): move the user to a new one-person house. */
        RECOVER_HOUSE
    }

    static final String UNNAMED = "사용자";

    private HouseholdSetup() {
    }

    public static Action decide(boolean userExists, String currentHouseId, boolean memberExists) {
        if (!userExists) {
            return Action.CREATE_FIRST_HOUSE;
        }
        if (currentHouseId == null || currentHouseId.isEmpty() || !memberExists) {
            return Action.RECOVER_HOUSE;
        }
        return Action.READY;
    }

    public static Map<String, Object> newUser(String houseId, String displayName, Object now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Schema.Users.CURRENT_HOUSE_ID, Objects.requireNonNull(houseId, "houseId"));
        fields.put(Schema.Users.DISPLAY_NAME, Objects.requireNonNull(displayName, "displayName"));
        fields.put(Schema.Users.NOTIFY_TIME, Schema.Users.DEFAULT_NOTIFY_TIME);
        fields.put(Schema.Users.ADVANCE_DAYS, Schema.Users.DEFAULT_ADVANCE_DAYS);
        fields.put(Schema.Users.CREATED_AT, now);
        fields.put(Schema.Users.UPDATED_AT, now);
        return fields;
    }

    /** Fields to point an existing user at a recovered house; other user settings stay. */
    public static Map<String, Object> moveUser(String houseId, Object now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Schema.Users.CURRENT_HOUSE_ID, Objects.requireNonNull(houseId, "houseId"));
        fields.put(Schema.Users.UPDATED_AT, now);
        return fields;
    }

    public static Map<String, Object> newHouse(String ownerId, Object now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Schema.Houses.OWNER_ID, Objects.requireNonNull(ownerId, "ownerId"));
        fields.put(Schema.Houses.INTEGRITY_VERSION, 2);
        fields.put(Schema.Houses.MEMBER_COUNT, 1);
        fields.put(Schema.Houses.ITEM_COUNT, 0);
        fields.put(Schema.Houses.CHORE_COUNT, 0);
        fields.put(Schema.Houses.CREATED_AT, now);
        fields.put(Schema.Houses.UPDATED_AT, now);
        return fields;
    }

    public static Map<String, Object> newMember(String displayName, Object now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Schema.Members.DISPLAY_NAME, Objects.requireNonNull(displayName, "displayName"));
        fields.put(Schema.Members.JOINED_AT, now);
        return fields;
    }

    /**
     * Display name for a user document created outside sign-up (e.g. sign-up stopped before the
     * house was written): the Auth profile name if valid, else the email's local part, else a default.
     */
    public static String fallbackDisplayName(String profileName, String email) {
        String name = AccountInputValidator.normalizeName(profileName);
        if (isValidName(name)) {
            return name;
        }
        String local = email == null ? "" : AccountInputValidator.normalizeEmail(email);
        int at = local.indexOf('@');
        if (at >= 0) {
            local = local.substring(0, at);
        }
        if (local.isEmpty()) {
            return UNNAMED;
        }
        int end = local.offsetByCodePoints(0, Math.min(AccountInputValidator.NAME_MAX_LENGTH,
                local.codePointCount(0, local.length())));
        return local.substring(0, end);
    }

    private static boolean isValidName(String name) {
        return !name.isEmpty() && name.codePointCount(0, name.length()) <= AccountInputValidator.NAME_MAX_LENGTH;
    }
}
