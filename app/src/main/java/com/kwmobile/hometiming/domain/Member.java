package com.kwmobile.hometiming.domain;

import java.util.Objects;

/** A member of a house (docs/DATA_MODEL.md members). */
public final class Member {
    public final String uid;
    public final String displayName;
    public final boolean owner;

    public Member(String uid, String displayName, boolean owner) {
        this.uid = Objects.requireNonNull(uid, "uid");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.owner = owner;
    }
}
