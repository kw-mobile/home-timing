package com.kwmobile.hometiming.data.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/** Account/house transitions and cache-listener ownership, rather than individual helper methods. */
public class OperationScopeTest {
    @Test
    public void sameAccountReloginDiscardsOldWorkAndCachedSubscriptions() {
        OperationScope scope = new OperationScope();
        scope.invalidate();
        long firstLogin = scope.generation();
        assertTrue(scope.bindIfGeneration(firstLogin, "alice", "home-a"));
        OperationScope.Token pendingWrite = scope.capture("alice", "home-a");
        AtomicInteger removals = new AtomicInteger();
        scope.onInvalidated(pendingWrite, removals::incrementAndGet);

        scope.invalidate(); // Sign-out, followed by signing back into the same account.
        assertEquals(1, removals.get());
        assertTrue(scope.bindIfGeneration(scope.generation(), "alice", "home-a"));
        assertFalse(scope.isCurrent(pendingWrite, "alice"));
        assertFalse(scope.bindIfGeneration(firstLogin, "alice", "home-a"));
        assertNull(scope.capture("bob", "home-a"));
        assertNull(scope.capture("alice", "another-house"));
        assertNotNull(scope.capture("alice", "home-a"));
        // Attaching a Firestore listener after invalidation cannot leave it subscribed.
        scope.onInvalidated(pendingWrite, removals::incrementAndGet);
        assertEquals(2, removals.get());
    }

    @Test
    public void movingHouseRejectsAnEarlierRestoreAndDetachesOldHouseOnly() {
        OperationScope scope = new OperationScope();
        assertTrue(scope.bindIfGeneration(scope.generation(), "alice", "home-a"));
        long oldRestore = scope.generation();
        OperationScope.Token move = scope.capture("alice");
        AtomicInteger removals = new AtomicInteger();
        OperationScope.Registration removedByScreen = scope.onInvalidated(move, removals::incrementAndGet);
        removedByScreen.remove();
        scope.onInvalidated(move, removals::incrementAndGet);

        assertTrue(scope.move(move, "alice", "home-b"));
        assertEquals(1, removals.get());
        assertFalse(scope.isCurrent(move, "alice"));
        assertFalse(scope.bindIfGeneration(oldRestore, "alice", "home-a"));
        assertNull(scope.capture("alice", "home-a"));
        assertNotNull(scope.capture("alice", "home-b"));
        scope.invalidate();
        assertFalse(scope.isCurrent(scope.capture("alice"), "alice"));
        long epoch = scope.generation();
        assertTrue(scope.bindIfGeneration(epoch, "alice", "home-b"));
        assertFalse(scope.bindIfGeneration(epoch, "alice", "home-a"));
        long slowRestore = scope.beginRestore(epoch);
        long freshRestore = scope.beginRestore(epoch);
        // Merely checking the same house offline must not remove its subscriptions/reservations.
        AtomicInteger sameHouseRemoval = new AtomicInteger();
        scope.onInvalidated(scope.capture("alice"), sameHouseRemoval::incrementAndGet);
        assertTrue(scope.bindRestoredIfCurrent(epoch, freshRestore, "alice", "home-b"));
        assertEquals(0, sameHouseRemoval.get());
        assertFalse(scope.bindRestoredIfCurrent(epoch, slowRestore, "alice", "home-a"));
        long movedRestore = scope.beginRestore(epoch);
        assertTrue(scope.bindRestoredIfCurrent(epoch, movedRestore, "alice", "home-c"));
        assertEquals(1, sameHouseRemoval.get());
        assertNull(scope.capture("alice", "home-b"));
    }
}
