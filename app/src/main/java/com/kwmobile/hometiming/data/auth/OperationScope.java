package com.kwmobile.hometiming.data.auth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The current account and house of UI-facing data operations. Auth binds the scope after restoring
 * its own user document; changing account or house invalidates pending work and removes listeners.
 * Firebase still authorizes every server request; this scope also protects local cached callbacks.
 */
public final class OperationScope {
    public interface Registration {
        void remove();
    }

    public static final class Token {
        public final String uid;
        public final String houseId;
        private final long generation;

        private Token(String uid, String houseId, long generation) {
            this.uid = uid;
            this.houseId = houseId;
            this.generation = generation;
        }
    }

    private static final OperationScope INSTANCE = new OperationScope();
    private final Set<Runnable> removals = new HashSet<>();
    private String uid;
    private String houseId;
    private long generation;
    private long restoreSequence;

    OperationScope() {
    }

    public static OperationScope getInstance() {
        return INSTANCE;
    }

    public synchronized long generation() {
        return generation;
    }

    /** A logout, new login attempt or account deletion starts a distinct operation epoch. */
    public void invalidate() {
        List<Runnable> detached;
        synchronized (this) {
            generation++;
            uid = null;
            houseId = null;
            detached = detach();
        }
        detached.forEach(Runnable::run);
    }

    /** Newer restores supersede older results without dropping same-house offline reservations. */
    public synchronized long beginRestore(long expectedGeneration) {
        return generation == expectedGeneration ? ++restoreSequence : -1;
    }

    public synchronized boolean isCurrentRestore(long expectedGeneration, long request) {
        return generation == expectedGeneration && restoreSequence == request;
    }

    public boolean bindRestoredIfCurrent(long expected, long request, String newUid, String newHouseId) {
        return bind(expected, request, newUid, newHouseId);
    }

    /** Login/deletion callers may bind a fresh epoch, but cannot replace an already-bound house. */
    public boolean bindIfGeneration(long expected, String newUid, String newHouseId) {
        return bind(expected, -1, newUid, newHouseId);
    }

    private boolean bind(long expected, long restoreRequest, String newUid, String newHouseId) {
        List<Runnable> detached = new ArrayList<>();
        synchronized (this) {
            if (generation != expected || (restoreRequest >= 0 && restoreRequest != restoreSequence)
                    || newUid == null || newHouseId == null || newHouseId.isEmpty()) {
                return false;
            }
            if (uid != null && (!uid.equals(newUid) || !houseId.equals(newHouseId))) {
                if (restoreRequest < 0) return false;
                generation++;
                detached = detach();
            }
            uid = newUid;
            houseId = newHouseId;
        }
        detached.forEach(Runnable::run);
        return true;
    }

    public synchronized Token capture(String expectedUid, String expectedHouseId) {
        return uid != null && uid.equals(expectedUid) && houseId.equals(expectedHouseId)
                ? new Token(uid, houseId, generation) : null;
    }

    /** Used by joining, whose destination is not yet the current house. */
    public synchronized Token capture(String expectedUid) {
        return uid != null && uid.equals(expectedUid) ? new Token(uid, houseId, generation) : null;
    }

    public synchronized boolean isCurrent(Token token, String liveUid) {
        return token != null && token.generation == generation && Objects.equals(token.uid, uid)
                && Objects.equals(token.houseId, houseId) && token.uid.equals(liveUid);
    }

    /** A committed house move is one state transition, invalidating old results and subscriptions. */
    public boolean move(Token token, String liveUid, String newHouseId) {
        List<Runnable> detached;
        synchronized (this) {
            if (!isCurrent(token, liveUid) || newHouseId == null || newHouseId.isEmpty()) {
                return false;
            }
            generation++;
            houseId = newHouseId;
            detached = detach();
        }
        detached.forEach(Runnable::run);
        return true;
    }

    /** The cleanup also runs immediately when registration races with invalidation. */
    public Registration onInvalidated(Token token, Runnable cleanup) {
        boolean stale;
        synchronized (this) {
            stale = !isCurrent(token, uid);
            if (!stale) {
                removals.add(cleanup);
            }
        }
        if (stale) {
            cleanup.run();
        }
        return () -> {
            synchronized (OperationScope.this) {
                removals.remove(cleanup);
            }
        };
    }

    private List<Runnable> detach() {
        List<Runnable> detached = new ArrayList<>(removals);
        removals.clear();
        return detached;
    }
}
