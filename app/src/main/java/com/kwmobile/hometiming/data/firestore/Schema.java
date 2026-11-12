package com.kwmobile.hometiming.data.firestore;

/** Firestore collection and field names from docs/DATA_MODEL.md. Change both together. */
public final class Schema {
    private Schema() {
    }

    /** Integrity fields shared by items and chores; absent legacy state is read-only. */
    public static final class Records {
        public static final String COUNT = "recordCount";
        public static final String MUTATION_ID = "recordMutationId";
        public static final String DELETING = "deleting";

        private Records() {
        }
    }

    /** Immutable minimal receipt retained after Auth deletion to prevent recreation by stale clients. */
    public static final class AccountDeletions {
        public static final String COLLECTION = "accountDeletions";
        public static final String STARTED_AT = "startedAt";

        private AccountDeletions() {
        }
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
        public static final String INTEGRITY_VERSION = "integrityVersion";
        public static final String CLOSING = "closing";
        public static final String ITEM_MUTATION_ID = "itemMutationId";
        public static final String CHORE_MUTATION_ID = "choreMutationId";
        public static final String MEMBER_MUTATION_ID = "memberMutationId";
        public static final String MEMBER_COUNT = "memberCount";
        public static final String ITEM_COUNT = "itemCount";
        public static final String CHORE_COUNT = "choreCount";
        public static final String REGION_NAME = "regionName";
        public static final String NX = "nx";
        public static final String NY = "ny";
        public static final String ACTIVE_INVITE_CODE = "activeInviteCode";
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
        public static final String INVITE_CODE = "inviteCode";

        private Members() {
        }
    }

    public static final class InviteLookups {
        public static final String COLLECTION = "inviteLookups";
        public static final String CODE = "code";
        public static final String PURPOSE = "purpose";
        public static final String WINDOW_STARTED_AT = "windowStartedAt";
        public static final String ATTEMPTS = "attempts";
        public static final String UPDATED_AT = "updatedAt";

        private InviteLookups() {
        }
    }

    public static final class Invites {
        public static final String COLLECTION = "invites";
        public static final String HOUSE_ID = "houseId";
        public static final String CREATED_BY = "createdBy";
        public static final String CREATED_AT = "createdAt";

        private Invites() {
        }
    }

    public static final class Items {
        public static final String COLLECTION = "items";
        public static final String NAME = "name";
        public static final String CATEGORY = "category";
        public static final String START_DATE = "startDate";
        public static final String CYCLE_DAYS = "cycleDays";
        public static final String LAST_REPLACED_DATE = "lastReplacedDate";
        public static final String NEXT_DUE_DATE = "nextDueDate";
        public static final String OCCURRENCE_ID = "occurrenceId";
        public static final String REVISION = "revision";
        public static final String PRODUCT_NAME = "productName";
        public static final String LOCATION = "location";
        public static final String MEMO = "memo";
        public static final String CREATED_BY = "createdBy";
        public static final String UPDATED_BY = "updatedBy";
        public static final String CREATED_AT = "createdAt";
        public static final String UPDATED_AT = "updatedAt";

        private Items() {
        }
    }

    public static final class Chores {
        public static final String COLLECTION = "chores";
        public static final String NAME = "name";
        public static final String TYPE = "type";
        public static final String WEATHER_SENSITIVE = "weatherSensitive";
        public static final String WEEKDAY = "weekday";
        public static final String PLANNED_DUE_DATE = "plannedDueDate";
        public static final String NEXT_DUE_DATE = "nextDueDate";
        public static final String OCCURRENCE_ID = "occurrenceId";
        public static final String REVISION = "revision";
        public static final String DISMISSED_OCCURRENCE_ID = "dismissedOccurrenceId";
        public static final String LAST_DONE_DATE = "lastDoneDate";
        public static final String LOCATION = "location";
        public static final String MEMO = "memo";
        public static final String CREATED_BY = "createdBy";
        public static final String UPDATED_BY = "updatedBy";
        public static final String CREATED_AT = "createdAt";
        public static final String UPDATED_AT = "updatedAt";

        private Chores() {
        }
    }

    public static final class Logs {
        public static final String COLLECTION = "logs";
        public static final String ACTION = "action";
        public static final String OCCURRENCE_ID = "occurrenceId";
        public static final String DATE = "date";
        public static final String FROM_DATE = "fromDate";
        public static final String REASON = "reason";
        public static final String ACTOR_ID = "actorId";
        public static final String ACTOR_NAME = "actorName";
        public static final String CREATED_AT = "createdAt";

        public static final String ACTION_DONE = "DONE";
        public static final String ACTION_RESCHEDULED = "RESCHEDULED";
        public static final String REASON_RAIN_FORECAST = "RAIN_FORECAST";
        public static final String REASON_USER = "USER";

        private Logs() {
        }
    }

    public static final class History {
        public static final String COLLECTION = "history";
        public static final String REPLACED_DATE = "replacedDate";
        public static final String PREVIOUS_DUE_DATE = "previousDueDate";
        public static final String CYCLE_DAYS_USED = "cycleDaysUsed";
        public static final String COMPLETED_BY = "completedBy";
        public static final String COMPLETED_BY_NAME = "completedByName";
        public static final String CREATED_AT = "createdAt";

        private History() {
        }
    }
}
