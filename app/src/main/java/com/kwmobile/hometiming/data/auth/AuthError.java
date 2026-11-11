package com.kwmobile.hometiming.data.auth;

import androidx.annotation.StringRes;

import com.kwmobile.hometiming.R;

/** Why an account or house-setup request failed, with the Korean message to show. */
public enum AuthError {
    INVALID_INPUT(R.string.auth_error_invalid_input),
    INVALID_EMAIL(R.string.auth_error_invalid_email),
    WEAK_PASSWORD(R.string.auth_error_weak_password),
    EMAIL_IN_USE(R.string.auth_error_email_in_use),
    WRONG_CREDENTIALS(R.string.auth_error_wrong_credentials),
    USER_DISABLED(R.string.auth_error_user_disabled),
    TOO_MANY_ATTEMPTS(R.string.auth_error_too_many_attempts),
    NETWORK(R.string.auth_error_network),
    /** Security rules or the server rejected the house setup. */
    PERMISSION_DENIED(R.string.auth_error_permission_denied),
    /** Built without app/google-services.json. */
    NOT_CONFIGURED(R.string.auth_error_not_configured),
    NOT_SIGNED_IN(R.string.auth_error_not_signed_in),
    /** Account deletion: the owner must remove the other members first (no owner transfer in P0). */
    OWNER_HAS_MEMBERS(R.string.auth_error_owner_has_members),
    /** Account deletion: data is cleaned up but the Auth account is not deleted yet; call again. */
    DELETE_INCOMPLETE(R.string.auth_error_delete_incomplete),
    UNKNOWN(R.string.auth_error_unknown);

    @StringRes
    public final int messageRes;

    AuthError(@StringRes int messageRes) {
        this.messageRes = messageRes;
    }
}
