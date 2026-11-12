package com.kwmobile.hometiming.data.firestore;

import androidx.annotation.StringRes;

import com.kwmobile.hometiming.R;

/** Why a family-sharing request failed, with the Korean message to show (docs/DATA_MODEL.md 가족 공유). */
public enum HouseError {
    INVALID_INPUT(R.string.house_error_invalid_input),
    /** No such code, or it expired. */
    INVALID_CODE(R.string.house_error_invalid_code),
    /** The server refused the join: the house is full or the code was replaced meanwhile. */
    CODE_REJECTED(R.string.house_error_code_rejected),
    /** In someone else's house: leave first. */
    LEAVE_FIRST(R.string.house_error_leave_first),
    /** Own house still has members, items or chores. */
    HOUSE_NOT_EMPTY(R.string.house_error_house_not_empty),
    ALREADY_MEMBER(R.string.house_error_already_member),
    NOT_OWNER(R.string.house_error_not_owner),
    OWNER_CANNOT_LEAVE(R.string.house_error_owner_cannot_leave),
    NOT_FOUND(R.string.house_error_not_found),
    PERMISSION_DENIED(R.string.house_error_permission_denied),
    NETWORK(R.string.house_error_network),
    RATE_LIMITED(R.string.house_error_rate_limited),
    NOT_CONFIGURED(R.string.house_error_not_configured),
    NOT_SIGNED_IN(R.string.house_error_not_signed_in),
    UNKNOWN(R.string.house_error_unknown);

    @StringRes
    public final int messageRes;

    HouseError(@StringRes int messageRes) {
        this.messageRes = messageRes;
    }

    /** {@code FirebaseFirestoreException.getCode().name()}. */
    static HouseError fromFirestoreCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        switch (code) {
            case "UNAVAILABLE":
            case "DEADLINE_EXCEEDED":
                return NETWORK;
            case "PERMISSION_DENIED":
            case "UNAUTHENTICATED":
                return PERMISSION_DENIED;
            case "NOT_FOUND":
                return NOT_FOUND;
            default:
                return UNKNOWN;
        }
    }
}
