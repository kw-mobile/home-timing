package com.kwmobile.hometiming.data.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AuthSessionGuardTest {
    @Test
    public void signOutAndSameUserSignInDiscardEarlierRestoreAndDeletion() {
        AuthSessionGuard sessions = new AuthSessionGuard();
        long signIn = sessions.begin();
        long restore = sessions.restoreGeneration();
        assertTrue(sessions.accepts(signIn, "alice", "alice"));
        assertTrue(sessions.accepts(restore, "alice", "alice"));

        long deletion = sessions.beginDeletion();
        assertTrue(sessions.restoreGeneration() < 0);
        assertFalse(sessions.accepts(restore, "alice", "alice"));
        assertTrue(sessions.accepts(deletion, "alice", "alice"));

        // Resolving a failed deletion permits a deliberate subsequent restore/retry.
        sessions.finishDeletion(deletion);
        assertTrue(sessions.restoreGeneration() == deletion);
        long previousDeletion = deletion;
        deletion = sessions.beginDeletion();
        sessions.finishDeletion(previousDeletion);
        assertTrue(sessions.restoreGeneration() < 0);

        sessions.begin(); // Sign-out invalidates already-running cleanup.
        assertFalse(sessions.accepts(deletion, "alice", null));
        long sameUserSignIn = sessions.begin();
        sessions.finishDeletion(deletion); // An old completion cannot alter the new session.
        assertTrue(sessions.restoreGeneration() == sameUserSignIn);
        assertFalse(sessions.accepts(deletion, "alice", "alice"));
        assertFalse(sessions.accepts(signIn, "alice", "alice"));
        assertTrue(sessions.accepts(sameUserSignIn, "alice", "alice"));
        assertFalse(sessions.accepts(sameUserSignIn, "alice", "bob"));
    }
}
