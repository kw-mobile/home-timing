package com.kwmobile.hometiming.data.firestore;

import androidx.annotation.StringRes;

import com.kwmobile.hometiming.R;

/** Why an item request failed, with the Korean message to show (docs/MVP.md 완료·반복 계약). */
public enum ItemError {
    INVALID_INPUT(R.string.item_error_invalid_input),
    /** The occurrence on screen was already completed; reload before completing again. */
    ALREADY_COMPLETED(R.string.item_error_already_completed),
    /** The schedule changed since the screen loaded it; reload and retry. */
    SCHEDULE_CHANGED(R.string.item_error_schedule_changed),
    NOT_FOUND(R.string.item_error_not_found),
    /** Only the registrant or the house owner may delete. */
    NOT_ALLOWED(R.string.item_error_not_allowed),
    /** Security rules rejected the write (e.g. no longer a member). */
    PERMISSION_DENIED(R.string.item_error_permission_denied),
    /** Offline or unreachable: nothing was saved. */
    NETWORK(R.string.item_error_network),
    NOT_CONFIGURED(R.string.item_error_not_configured),
    NOT_SIGNED_IN(R.string.item_error_not_signed_in),
    UNKNOWN(R.string.item_error_unknown);

    @StringRes
    public final int messageRes;

    ItemError(@StringRes int messageRes) {
        this.messageRes = messageRes;
    }

    /** {@code FirebaseFirestoreException.getCode().name()}. */
    static ItemError fromFirestoreCode(String code) {
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
            // A history document for the finished occurrence already exists.
            case "ALREADY_EXISTS":
                return ALREADY_COMPLETED;
            default:
                return UNKNOWN;
        }
    }
}
