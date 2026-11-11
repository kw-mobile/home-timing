package com.kwmobile.hometiming.data.firestore;

import androidx.annotation.NonNull;

/** Failure of a {@link HouseRepository} task; {@link #error} picks the message to show. */
public final class HouseException extends Exception {
    @NonNull
    public final HouseError error;

    public HouseException(@NonNull HouseError error) {
        super(error.name());
        this.error = error;
    }

    public HouseException(@NonNull HouseError error, Throwable cause) {
        super(error.name(), cause);
        this.error = error;
    }

    /** Thrown inside a transaction function, which cannot throw checked exceptions. */
    static final class Rejection extends RuntimeException {
        final HouseError error;

        Rejection(HouseError error) {
            super(error.name());
            this.error = error;
        }
    }
}
