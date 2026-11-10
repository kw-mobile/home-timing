package com.kwmobile.hometiming.data.auth;

/**
 * Maps Firebase error codes to {@link AuthError}. Codes are passed as strings so the mapping is
 * unit-tested without Firebase exception instances.
 */
final class AuthErrorMapper {
    private AuthErrorMapper() {
    }

    /** {@code FirebaseAuthException.getErrorCode()}. */
    static AuthError fromAuthCode(String code) {
        if (code == null) {
            return AuthError.UNKNOWN;
        }
        switch (code) {
            case "ERROR_INVALID_EMAIL":
                return AuthError.INVALID_EMAIL;
            case "ERROR_WEAK_PASSWORD":
                return AuthError.WEAK_PASSWORD;
            case "ERROR_EMAIL_ALREADY_IN_USE":
                return AuthError.EMAIL_IN_USE;
            // With email enumeration protection, unknown users and wrong passwords look the same.
            case "ERROR_WRONG_PASSWORD":
            case "ERROR_USER_NOT_FOUND":
            case "ERROR_INVALID_CREDENTIAL":
                return AuthError.WRONG_CREDENTIALS;
            case "ERROR_USER_DISABLED":
                return AuthError.USER_DISABLED;
            case "ERROR_TOO_MANY_REQUESTS":
                return AuthError.TOO_MANY_ATTEMPTS;
            case "ERROR_NETWORK_REQUEST_FAILED":
                return AuthError.NETWORK;
            default:
                return AuthError.UNKNOWN;
        }
    }

    /** {@code FirebaseFirestoreException.getCode().name()}. */
    static AuthError fromFirestoreCode(String code) {
        if (code == null) {
            return AuthError.UNKNOWN;
        }
        switch (code) {
            case "UNAVAILABLE":
            case "DEADLINE_EXCEEDED":
                return AuthError.NETWORK;
            case "PERMISSION_DENIED":
            case "UNAUTHENTICATED":
                return AuthError.PERMISSION_DENIED;
            default:
                return AuthError.UNKNOWN;
        }
    }
}
