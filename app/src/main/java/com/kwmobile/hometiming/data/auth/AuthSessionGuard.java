package com.kwmobile.hometiming.data.auth;

/** Invalidates callbacks across account operations, including signing back into the same account. */
final class AuthSessionGuard {
    private long generation;
    private long deletingGeneration = -1;

    synchronized long begin() {
        deletingGeneration = -1;
        return ++generation;
    }

    synchronized long beginDeletion() {
        long operation = begin();
        deletingGeneration = operation;
        return operation;
    }

    /** A restore must never recreate data while deletion is between data cleanup and Auth deletion. */
    synchronized long restoreGeneration() {
        return deletingGeneration == generation ? -1 : generation;
    }

    synchronized void finishDeletion(long operation) {
        if (deletingGeneration == operation) {
            deletingGeneration = -1;
        }
    }

    synchronized boolean accepts(long operation, String expectedUid, String currentUid) {
        return operation == generation && expectedUid != null && expectedUid.equals(currentUid);
    }

    synchronized boolean accepts(long operation) {
        return operation == generation;
    }
}
