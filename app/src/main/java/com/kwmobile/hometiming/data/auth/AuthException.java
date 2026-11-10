package com.kwmobile.hometiming.data.auth;

import androidx.annotation.NonNull;

/** Failure of an {@link AuthRepository} task; {@link #error} picks the message to show. */
public final class AuthException extends Exception {
    @NonNull
    public final AuthError error;

    public AuthException(@NonNull AuthError error) {
        super(error.name());
        this.error = error;
    }

    public AuthException(@NonNull AuthError error, Throwable cause) {
        super(error.name(), cause);
        this.error = error;
    }
}
