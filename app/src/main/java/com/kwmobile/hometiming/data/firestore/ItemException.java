package com.kwmobile.hometiming.data.firestore;

import androidx.annotation.NonNull;

/** Failure of an {@link ItemRepository} task; {@link #error} picks the message to show. */
public final class ItemException extends Exception {
    @NonNull
    public final ItemError error;

    public ItemException(@NonNull ItemError error) {
        super(error.name());
        this.error = error;
    }

    public ItemException(@NonNull ItemError error, Throwable cause) {
        super(error.name(), cause);
        this.error = error;
    }

    /** Thrown inside a transaction function, which cannot throw checked exceptions. */
    static final class Rejection extends RuntimeException {
        final ItemError error;

        Rejection(ItemError error) {
            super(error.name());
            this.error = error;
        }
    }
}
