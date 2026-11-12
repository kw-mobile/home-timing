package com.kwmobile.hometiming.data.auth;

/** Invalidates callbacks across account operations, including signing back into the same account. */
final class AuthSessionGuard {
    private final OperationScope scope = OperationScope.getInstance();
    private long deletingGeneration = -1;

    synchronized long begin() {
        deletingGeneration = -1;
        scope.invalidate();
        return scope.generation();
    }

    synchronized long beginDeletion() {
        long operation = begin();
        deletingGeneration = operation;
        return operation;
    }

    /** A restore must never recreate data while deletion is between data cleanup and Auth deletion. */
    synchronized long restoreGeneration() {
        return deletingGeneration == scope.generation() ? -1 : scope.generation();
    }

    synchronized void finishDeletion(long operation) {
        if (deletingGeneration == operation) {
            deletingGeneration = -1;
        }
    }

    synchronized boolean accepts(long operation, String expectedUid, String currentUid) {
        return operation == scope.generation() && expectedUid != null && expectedUid.equals(currentUid);
    }

    synchronized boolean accepts(long operation) {
        return operation == scope.generation();
    }
}
