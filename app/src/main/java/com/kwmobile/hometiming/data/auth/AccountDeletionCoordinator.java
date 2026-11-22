package com.kwmobile.hometiming.data.auth;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.kwmobile.hometiming.data.firestore.ChoreRepository;
import com.kwmobile.hometiming.data.firestore.HouseDocuments;
import com.kwmobile.hometiming.data.firestore.ItemRepository;
import com.kwmobile.hometiming.data.firestore.Schema;
import com.kwmobile.hometiming.domain.AccountDeletion;
import com.kwmobile.hometiming.domain.House;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** One account-deletion attempt: freeze shared state, then resume its data cleanup. */
final class AccountDeletionCoordinator {
    private final FirebaseFirestore firestore;
    private final AuthSessionGuard sessions;
    private final Supplier<String> currentUid;
    private final String uid;
    private final long generation;

    AccountDeletionCoordinator(FirebaseFirestore firestore, AuthSessionGuard sessions,
                               Supplier<String> currentUid, String uid, long generation) {
        this.firestore = firestore;
        this.sessions = sessions;
        this.currentUid = currentUid;
        this.uid = uid;
        this.generation = generation;
    }

    Task<Void> removeData() throws AuthException {
        return beginAccountDeletion().onSuccessTask(ignored -> cleanUpAccountData());
    }

    /** Freezes account/house state before any destructive cleanup, even across devices. */
    private Task<Void> beginAccountDeletion() throws AuthException {
        requireSession();
        DocumentReference receipt = firestore.collection(Schema.AccountDeletions.COLLECTION).document(uid);
        DocumentReference user = firestore.collection(Schema.Users.COLLECTION).document(uid);
        return firestore.runTransaction(tx -> {
            requireTransactionSession();
            DocumentSnapshot marker = tx.get(receipt);
            DocumentSnapshot profile = tx.get(user);
            DocumentReference accessRef = firestore.collection(Schema.InviteLookups.COLLECTION).document(uid);
            DocumentSnapshot access = tx.get(accessRef);
            String houseId = profile.getString(Schema.Users.CURRENT_HOUSE_ID);
            DocumentReference houseRef = houseId == null ? null : firestore.collection(Schema.Houses.COLLECTION).document(houseId);
            DocumentReference memberRef = houseId == null ? null : memberRef(firestore, houseId, uid);
            DocumentSnapshot member = memberRef == null ? null : tx.get(memberRef);
            DocumentSnapshot home = member != null && member.exists() ? tx.get(houseRef) : null;
            House house = home == null || home.getData() == null ? null : HouseDocuments.toHouse(houseId, home.getData());
            if (house != null && house.isOwner(uid) && house.memberCount > 1) {
                throw new IllegalStateException(new AuthException(AuthError.OWNER_HAS_MEMBERS));
            }
            DocumentReference inviteRef = house == null || house.activeInviteCode == null ? null
                    : firestore.collection(Schema.Invites.COLLECTION).document(house.activeInviteCode);
            DocumentSnapshot invite = inviteRef == null ? null : tx.get(inviteRef);
            requireTransactionSession();
            if (!marker.exists()) {
                tx.set(receipt, java.util.Collections.singletonMap(Schema.AccountDeletions.STARTED_AT,
                        FieldValue.serverTimestamp()));
            }
            if (access.exists()) {
                tx.delete(accessRef);
            }
            if (house != null && house.isOwner(uid)) {
                if (!Boolean.TRUE.equals(home.getBoolean(Schema.Houses.CLOSING))) {
                    tx.update(houseRef, Schema.Houses.CLOSING, true,
                            Schema.Houses.ACTIVE_INVITE_CODE, FieldValue.delete(),
                            Schema.Houses.UPDATED_AT, FieldValue.serverTimestamp());
                }
                if (invite != null && invite.exists() && houseId.equals(invite.getString(Schema.Invites.HOUSE_ID))) {
                    tx.delete(inviteRef);
                }
                return houseId;
            }
            if (member != null && member.exists()) {
                tx.delete(memberRef);
                tx.update(houseRef, Schema.Houses.MEMBER_COUNT, FieldValue.increment(-1),
                        Schema.Houses.MEMBER_MUTATION_ID, uid);
            }
            if (profile.exists()) {
                tx.delete(user);
            }
            return null;
        }).onSuccessTask(houseId -> {
            requireSession();
            if (houseId != null && !OperationScope.getInstance().bindIfGeneration(generation, uid, houseId)) {
                return fail(AuthError.NOT_SIGNED_IN);
            }
            return Tasks.forResult(null);
        });
    }

    private Task<Void> cleanUpAccountData() throws AuthException {
        requireSession();
        DocumentReference userRef = firestore.collection(Schema.Users.COLLECTION).document(uid);
        return userRef.get(Source.SERVER).onSuccessTask(userSnapshot -> {
            requireSession();
            String houseId = userSnapshot.exists() ? userSnapshot.getString(Schema.Users.CURRENT_HOUSE_ID) : null;
            if (!userSnapshot.exists()) {
                return Tasks.forResult(null);
            }
            if (houseId == null || houseId.isEmpty()) {
                return deleteUserOnly(userRef);
            }
            DocumentReference houseRef = firestore.collection(Schema.Houses.COLLECTION).document(houseId);
            DocumentReference memberRef = memberRef(firestore, houseId, uid);
            return memberRef.get(Source.SERVER).onSuccessTask(memberSnapshot -> {
                requireSession();
                if (!memberSnapshot.exists()) {
                    return deleteUserOnly(userRef);
                }
                return houseRef.get(Source.SERVER).onSuccessTask(houseSnapshot -> {
                    requireSession();
                    Map<String, Object> data = houseSnapshot.getData();
                    House house = data == null ? null : HouseDocuments.toHouse(houseId, data);
                    switch (AccountDeletion.plan(true, house, true, uid)) {
                        case BLOCKED_OWNER_WITH_MEMBERS:
                            return fail(AuthError.OWNER_HAS_MEMBERS);
                        case LEAVE_SHARED_HOUSE:
                            return firestore.runTransaction(tx -> {
                                requireTransactionSession();
                                tx.delete(memberRef);
                                tx.update(houseRef, Schema.Houses.MEMBER_COUNT, FieldValue.increment(-1),
                                        Schema.Houses.MEMBER_MUTATION_ID, uid);
                                tx.delete(userRef);
                                return null;
                            });
                        case DISSOLVE_OWN_HOUSE:
                            return deleteAllItems(houseId)
                                    .onSuccessTask(ignored -> deleteAllChores(houseId))
                                    .onSuccessTask(ignored -> dissolveOwnHouse(houseRef, memberRef, userRef));
                        case DELETE_USER_ONLY:
                        case DELETE_AUTH_ONLY:
                        default:
                            return deleteUserOnly(userRef);
                    }
                });
            });
        });
    }

    private Task<Void> deleteUserOnly(DocumentReference userRef) throws AuthException {
        requireSession();
        return firestore.runTransaction(tx -> {
            requireTransactionSession();
            tx.delete(userRef);
            return null;
        });
    }

    /** Items one by one through ItemRepository (history first, itemCount −1 with each item). */
    private Task<Void> deleteAllItems(String houseId) throws AuthException {
        requireSession();
        return firestore.collection(Schema.Houses.COLLECTION).document(houseId)
                .collection(Schema.Items.COLLECTION).get(Source.SERVER)
                .onSuccessTask(items -> {
                    List<String> ids = new ArrayList<>();
                    for (DocumentSnapshot document : items.getDocuments()) {
                        ids.add(document.getId());
                    }
                    Task<Void> chain = Tasks.forResult(null);
                    for (String itemId : ids) {
                        chain = chain.onSuccessTask(ignored -> {
                            requireSession();
                            return ItemRepository.getInstance().delete(houseId, itemId);
                        });
                    }
                    return chain;
                });
    }

    /** Chores one by one through ChoreRepository (logs first, choreCount −1 with each chore). */
    private Task<Void> deleteAllChores(String houseId) throws AuthException {
        requireSession();
        return firestore.collection(Schema.Houses.COLLECTION).document(houseId)
                .collection(Schema.Chores.COLLECTION).get(Source.SERVER)
                .onSuccessTask(chores -> {
                    List<String> ids = new ArrayList<>();
                    for (DocumentSnapshot document : chores.getDocuments()) {
                        ids.add(document.getId());
                    }
                    Task<Void> chain = Tasks.forResult(null);
                    for (String choreId : ids) {
                        chain = chain.onSuccessTask(ignored -> {
                            requireSession();
                            return ChoreRepository.getInstance().delete(houseId, choreId);
                        });
                    }
                    return chain;
                });
    }

    /** The empty one-person house, its invite, member and the user document in one transaction. */
    private Task<Void> dissolveOwnHouse(DocumentReference houseRef, DocumentReference memberRef,
                                        DocumentReference userRef) throws AuthException {
        requireSession();
        return firestore.runTransaction(tx -> {
            requireTransactionSession();
            DocumentSnapshot houseSnapshot = tx.get(houseRef);
            Map<String, Object> data = houseSnapshot.getData();
            House house = data == null ? null : HouseDocuments.toHouse(houseRef.getId(), data);
            DocumentReference inviteRef = house == null || house.activeInviteCode == null ? null
                    : firestore.collection(Schema.Invites.COLLECTION).document(house.activeInviteCode);
            DocumentSnapshot invite = inviteRef == null ? null : tx.get(inviteRef);
            if (house != null && !house.isEmptyOnePerson()) {
                // Something was added meanwhile; calling again deletes it first. Not a retryable
                // Firestore error, so the transaction stops instead of retrying the same state.
                throw new IllegalStateException("house not empty");
            }
            requireTransactionSession();
            if (invite != null && invite.exists() && houseRef.getId().equals(invite.getString(Schema.Invites.HOUSE_ID))) {
                tx.delete(inviteRef);
            }
            tx.delete(memberRef);
            if (house != null) {
                tx.delete(houseRef.collection("categoryCatalogs").document("item"));
                tx.delete(houseRef.collection("categoryCatalogs").document("chore"));
                tx.delete(houseRef);
            }
            tx.delete(userRef);
            return null;
        });
    }

    private static DocumentReference memberRef(FirebaseFirestore firestore, String houseId, String uid) {
        return firestore.collection(Schema.Houses.COLLECTION).document(houseId)
                .collection(Schema.Members.COLLECTION).document(uid);
    }

    private void requireSession() throws AuthException {
        if (!sessions.accepts(generation, uid, currentUid.get())) {
            throw new AuthException(AuthError.NOT_SIGNED_IN);
        }
    }

    private void requireTransactionSession() {
        if (!sessions.accepts(generation, uid, currentUid.get())) {
            throw new IllegalStateException(new AuthException(AuthError.NOT_SIGNED_IN));
        }
    }

    private static <T> Task<T> fail(AuthError error) {
        return Tasks.forException(new AuthException(error));
    }
}
