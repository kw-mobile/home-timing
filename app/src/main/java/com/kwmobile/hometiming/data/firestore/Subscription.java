package com.kwmobile.hometiming.data.firestore;

/**
 * A live listener a screen must stop when it goes away, the house changes or the user signs out
 * (docs/ARCHITECTURE.md 화면과 상태). Calling {@link #remove()} more than once is harmless.
 */
public interface Subscription {
    void remove();

    Subscription NONE = () -> { };
}
