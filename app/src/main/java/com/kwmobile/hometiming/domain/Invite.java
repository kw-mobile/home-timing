package com.kwmobile.hometiming.domain;

import java.time.Instant;
import java.util.Objects;

/** An open invite (docs/DATA_MODEL.md invites). Valid for 24 hours from the server creation time. */
public final class Invite {
    public final String code;
    public final String houseId;
    public final Instant createdAt;

    public Invite(String code, String houseId, Instant createdAt) {
        this.code = Objects.requireNonNull(code, "code");
        this.houseId = Objects.requireNonNull(houseId, "houseId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Instant expiresAt() {
        return InviteCodes.expiresAt(createdAt);
    }

    public boolean isLive(Instant now) {
        return InviteCodes.isLive(createdAt, now);
    }
}
