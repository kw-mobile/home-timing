package com.kwmobile.hometiming.domain;

/**
 * What deleting an account must clean up first (docs/MVP.md 데이터·권한 계약, docs/DATA_MODEL.md
 * 정리 정책). The Auth account is deleted only after these steps; each step checks the stored state,
 * so calling again after a failure continues where it stopped.
 */
public final class AccountDeletion {
    public enum Step {
        /** No user document left: only the Auth account remains to delete. */
        DELETE_AUTH_ONLY,
        /** User document without a membership (removed, not yet recovered): delete the user document. */
        DELETE_USER_ONLY,
        /** Member of someone else's house: leave it (member −1) with the user document. Items stay with the house. */
        LEAVE_SHARED_HOUSE,
        /** Alone in their own house: delete its items, invite, member and the house with the user document. */
        DISSOLVE_OWN_HOUSE,
        /** Owner while others remain: refuse until they are removed (owner transfer is out of P0). */
        BLOCKED_OWNER_WITH_MEMBERS
    }

    private AccountDeletion() {
    }

    /**
     * @param house the user's current house, or null if it is gone
     * @param memberExists whether the user's member document in that house exists
     */
    public static Step plan(boolean userExists, House house, boolean memberExists, String uid) {
        if (!userExists) {
            return Step.DELETE_AUTH_ONLY;
        }
        if (house == null || !memberExists) {
            return Step.DELETE_USER_ONLY;
        }
        if (!house.isOwner(uid)) {
            return Step.LEAVE_SHARED_HOUSE;
        }
        return house.memberCount > 1 ? Step.BLOCKED_OWNER_WITH_MEMBERS : Step.DISSOLVE_OWN_HOUSE;
    }
}
