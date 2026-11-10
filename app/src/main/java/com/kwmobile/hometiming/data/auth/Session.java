package com.kwmobile.hometiming.data.auth;

import androidx.annotation.NonNull;

/** The signed-in user and the house the app shows. */
public final class Session {
    @NonNull
    public final String uid;
    @NonNull
    public final String houseId;
    @NonNull
    public final String displayName;
    /**
     * True when restored from the local cache because the server was unreachable. Screens may show
     * cached data but must keep changes disabled (docs/MVP.md 완료·반복 계약).
     */
    public final boolean offline;

    public Session(@NonNull String uid, @NonNull String houseId, @NonNull String displayName, boolean offline) {
        this.uid = uid;
        this.houseId = houseId;
        this.displayName = displayName;
        this.offline = offline;
    }
}
