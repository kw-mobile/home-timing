package com.kwmobile.hometiming.data.firestore;

/** Firestore collection and field names from docs/DATA_MODEL.md. Change both together. */
public final class Schema {
    private Schema() {
    }

    public static final class Users {
        public static final String COLLECTION = "users";
        public static final String CURRENT_HOUSE_ID = "currentHouseId";
        public static final String DISPLAY_NAME = "displayName";
        public static final String NOTIFY_TIME = "notifyTime";
        public static final String ADVANCE_DAYS = "advanceDays";
        public static final String CREATED_AT = "createdAt";
        public static final String UPDATED_AT = "updatedAt";

        public static final String DEFAULT_NOTIFY_TIME = "09:00";
        public static final int DEFAULT_ADVANCE_DAYS = 3;

        private Users() {
        }
    }

    public static final class Houses {
        public static final String COLLECTION = "houses";
        public static final String OWNER_ID = "ownerId";
        public static final String MEMBER_COUNT = "memberCount";
        public static final String ITEM_COUNT = "itemCount";
        public static final String CHORE_COUNT = "choreCount";
        public static final String CREATED_AT = "createdAt";
        public static final String UPDATED_AT = "updatedAt";

        public static final int MAX_MEMBERS = 6;

        private Houses() {
        }
    }

    public static final class Members {
        public static final String COLLECTION = "members";
        public static final String DISPLAY_NAME = "displayName";
        public static final String JOINED_AT = "joinedAt";

        private Members() {
        }
    }
}
