package com.kwmobile.hometiming.data.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.data.firestore.ChoreRepository;
import com.kwmobile.hometiming.data.firestore.HouseDocuments;
import com.kwmobile.hometiming.data.firestore.HouseholdSetup;
import com.kwmobile.hometiming.data.firestore.ItemException;
import com.kwmobile.hometiming.data.firestore.ItemRepository;
import com.kwmobile.hometiming.data.firestore.Schema;
import com.kwmobile.hometiming.domain.AccountDeletion;
import com.kwmobile.hometiming.domain.AccountInputValidator;
import com.kwmobile.hometiming.domain.House;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Email accounts (docs/MVP.md F-01) and the one-person house every signed-in user belongs to.
 *
 * <p>Every result is a {@link Task}; failures are {@link AuthException}s whose {@link AuthError}
 * selects the message. Screens validate input with {@link AccountInputValidator} first; this class
 * checks again and reports {@link AuthError#INVALID_INPUT} instead of calling Firebase.
 */
public final class AuthRepository {
    private static volatile AuthRepository instance;
    private final AuthSessionGuard sessions = new AuthSessionGuard();

    @Nullable
    private final FirebaseAuth auth;
    @Nullable
    private final FirebaseFirestore db;

    private AuthRepository(@Nullable FirebaseAuth auth, @Nullable FirebaseFirestore db) {
        this.auth = auth;
        this.db = db;
    }

    /** Without app/google-services.json Firebase is never touched and every call fails NOT_CONFIGURED. */
    @NonNull
    public static AuthRepository getInstance() {
        AuthRepository local = instance;
        if (local == null) {
            synchronized (AuthRepository.class) {
                local = instance;
                if (local == null) {
                    local = BuildConfig.FIREBASE_CONFIGURED
                            ? new AuthRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
                            : new AuthRepository(null, null);
                    instance = local;
                }
            }
        }
        return local;
    }

    public boolean isConfigured() {
        return auth != null;
    }

    /** Auto sign-in check: true when a previous session is still stored on this device. */
    public boolean isSignedIn() {
        return auth != null && auth.getCurrentUser() != null;
    }

    /** Creates the account, stores the display name, then prepares the user's one-person house. */
    @NonNull
    public Task<Session> signUp(String displayName, String email, String password) {
        if (auth == null) {
            return fail(AuthError.NOT_CONFIGURED);
        }
        String name = AccountInputValidator.normalizeName(displayName);
        String address = AccountInputValidator.normalizeEmail(email);
        if (!AccountInputValidator.validateSignUp(name, address, password, password).isEmpty()) {
            return fail(AuthError.INVALID_INPUT);
        }
        long generation = sessions.begin();
        Task<Session> task = auth.createUserWithEmailAndPassword(address, password)
                .onSuccessTask(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        return Tasks.forException(new AuthException(AuthError.UNKNOWN));
                    }
                    requireSession(generation, user.getUid());
                    UserProfileChangeRequest profile = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name).build();
                    // The house uses the typed name even if the profile update fails.
                    return user.updateProfile(profile)
                            .continueWithTask(ignored -> prepareHouse(user.getUid(), name, generation));
                });
        return sessionResult(task, generation);
    }

    @NonNull
    public Task<Session> signIn(String email, String password) {
        if (auth == null) {
            return fail(AuthError.NOT_CONFIGURED);
        }
        String address = AccountInputValidator.normalizeEmail(email);
        if (!AccountInputValidator.validateSignIn(address, password).isEmpty()) {
            return fail(AuthError.INVALID_INPUT);
        }
        long generation = sessions.begin();
        Task<Session> task = auth.signInWithEmailAndPassword(address, password)
                .onSuccessTask(result -> {
                    FirebaseUser user = result.getUser();
                    return user == null
                            ? Tasks.forException(new AuthException(AuthError.UNKNOWN))
                            : prepareHouse(user, generation);
                });
        return sessionResult(task, generation);
    }

    /**
     * Restores the stored session at app start. Online, it also creates or recovers the house. When
     * the server is unreachable it falls back to the cached user document with {@code offline = true}.
     * Restoration is rejected while account deletion is running, so it cannot recreate cleaned data.
     */
    @NonNull
    public Task<Session> restoreSession() {
        if (auth == null) {
            return fail(AuthError.NOT_CONFIGURED);
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return fail(AuthError.NOT_SIGNED_IN);
        }
        long generation = sessions.restoreGeneration();
        if (generation < 0) {
            return fail(AuthError.NOT_SIGNED_IN);
        }
        Task<Session> restored = translate(prepareHouse(user, generation)).continueWithTask(task -> {
            requireSession(generation, user.getUid());
            if (task.isSuccessful() || errorOf(task.getException()) != AuthError.NETWORK) {
                return task;
            }
            return cachedSession(user.getUid(), generation).continueWithTask(cached ->
                    cached.isSuccessful() && cached.getResult() != null ? cached : task);
        });
        return sessionResult(restored, generation);
    }

    @NonNull
    public Task<Void> sendPasswordReset(String email) {
        if (auth == null) {
            return fail(AuthError.NOT_CONFIGURED);
        }
        String address = AccountInputValidator.normalizeEmail(email);
        if (!AccountInputValidator.validatePasswordReset(address).isEmpty()) {
            return fail(AuthError.INVALID_INPUT);
        }
        return translate(auth.sendPasswordResetEmail(address));
    }

    /** Signs out on this device. Listeners and local alarms are cleared by their owners. */
    public void signOut() {
        sessions.begin();
        if (auth != null) {
            auth.signOut();
        }
    }

    /** Auth deletion attempts after the data is cleaned up (network failures only). */
    static final int AUTH_DELETE_ATTEMPTS = 3;

    /**
     * Deletes the account (docs/DATA_MODEL.md 정리 정책): re-authenticates with the password, cleans
     * up the user's house data and user document, and only then deletes the Auth account.
     *
     * <p>Each step reads the stored state first, so after any failure calling this again continues
     * where it stopped. An owner whose house still has other members gets
     * {@link AuthError#OWNER_HAS_MEMBERS}; if only the Auth deletion fails the result is
     * {@link AuthError#DELETE_INCOMPLETE}. Items a member registered in a shared house stay there.
     */
    @NonNull
    public Task<Void> deleteAccount(String password) {
        if (auth == null || db == null) {
            return fail(AuthError.NOT_CONFIGURED);
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return fail(AuthError.NOT_SIGNED_IN);
        }
        String email = user.getEmail();
        if (email == null || password == null || password.isEmpty()) {
            return fail(AuthError.INVALID_INPUT);
        }
        long generation = sessions.beginDeletion();
        String uid = user.getUid();
        AuthCredential credential = EmailAuthProvider.getCredential(email, password);
        Task<Void> task = user.reauthenticate(credential)
                .onSuccessTask(ignored -> {
                    requireSession(generation, uid);
                    return cleanUpAccountData(db, uid, generation);
                })
                .onSuccessTask(ignored -> deleteAuth(user, AUTH_DELETE_ATTEMPTS, generation));
        return translate(task).continueWithTask(done -> {
            sessions.finishDeletion(generation);
            return sessions.accepts(generation) ? done : fail(AuthError.NOT_SIGNED_IN);
        });
    }

    private Task<Void> deleteAuth(FirebaseUser user, int attemptsLeft, long generation) throws AuthException {
        requireSession(generation, user.getUid());
        return user.delete().continueWithTask(done -> {
            if (done.isSuccessful()) {
                return done;
            }
            if (attemptsLeft > 1 && classify(done.getException()) == AuthError.NETWORK) {
                return deleteAuth(user, attemptsLeft - 1, generation);
            }
            return Tasks.forException(new AuthException(AuthError.DELETE_INCOMPLETE, done.getException()));
        });
    }

    private Task<Void> cleanUpAccountData(FirebaseFirestore firestore, String uid, long generation) throws AuthException {
        requireSession(generation, uid);
        DocumentReference userRef = firestore.collection(Schema.Users.COLLECTION).document(uid);
        return userRef.get(Source.SERVER).onSuccessTask(userSnapshot -> {
            requireSession(generation, uid);
            String houseId = userSnapshot.exists() ? userSnapshot.getString(Schema.Users.CURRENT_HOUSE_ID) : null;
            if (!userSnapshot.exists()) {
                return Tasks.forResult(null);
            }
            if (houseId == null || houseId.isEmpty()) {
                return deleteUserOnly(firestore, userRef, uid, generation);
            }
            DocumentReference houseRef = firestore.collection(Schema.Houses.COLLECTION).document(houseId);
            DocumentReference memberRef = memberRef(firestore, houseId, uid);
            return memberRef.get(Source.SERVER).onSuccessTask(memberSnapshot -> {
                requireSession(generation, uid);
                if (!memberSnapshot.exists()) {
                    return deleteUserOnly(firestore, userRef, uid, generation);
                }
                return houseRef.get(Source.SERVER).onSuccessTask(houseSnapshot -> {
                    requireSession(generation, uid);
                    Map<String, Object> data = houseSnapshot.getData();
                    House house = data == null ? null : HouseDocuments.toHouse(houseId, data);
                    switch (AccountDeletion.plan(true, house, true, uid)) {
                        case BLOCKED_OWNER_WITH_MEMBERS:
                            return fail(AuthError.OWNER_HAS_MEMBERS);
                        case LEAVE_SHARED_HOUSE:
                            return firestore.runTransaction(tx -> {
                                requireTransactionSession(generation, uid);
                                tx.delete(memberRef);
                                tx.update(houseRef, Schema.Houses.MEMBER_COUNT, FieldValue.increment(-1),
                                        Schema.Houses.MEMBER_MUTATION_ID, uid);
                                tx.delete(userRef);
                                return null;
                            });
                        case DISSOLVE_OWN_HOUSE:
                            return deleteAllItems(firestore, houseId, uid, generation)
                                    .onSuccessTask(ignored -> deleteAllChores(firestore, houseId, uid, generation))
                                    .onSuccessTask(ignored -> dissolveOwnHouse(firestore, houseRef, memberRef, userRef, uid, generation));
                        case DELETE_USER_ONLY:
                        case DELETE_AUTH_ONLY:
                        default:
                            return deleteUserOnly(firestore, userRef, uid, generation);
                    }
                });
            });
        });
    }

    private Task<Void> deleteUserOnly(FirebaseFirestore firestore, DocumentReference userRef,
                                      String uid, long generation) throws AuthException {
        requireSession(generation, uid);
        return firestore.runTransaction(tx -> {
            requireTransactionSession(generation, uid);
            tx.delete(userRef);
            return null;
        });
    }

    /** Items one by one through ItemRepository (history first, itemCount −1 with each item). */
    private Task<Void> deleteAllItems(FirebaseFirestore firestore, String houseId,
                                      String uid, long generation) throws AuthException {
        requireSession(generation, uid);
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
                            requireSession(generation, uid);
                            return ItemRepository.getInstance().delete(houseId, itemId);
                        });
                    }
                    return chain;
                });
    }

    /** Chores one by one through ChoreRepository (logs first, choreCount −1 with each chore). */
    private Task<Void> deleteAllChores(FirebaseFirestore firestore, String houseId,
                                      String uid, long generation) throws AuthException {
        requireSession(generation, uid);
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
                            requireSession(generation, uid);
                            return ChoreRepository.getInstance().delete(houseId, choreId);
                        });
                    }
                    return chain;
                });
    }

    /** The empty one-person house, its invite, member and the user document in one transaction. */
    private Task<Void> dissolveOwnHouse(FirebaseFirestore firestore, DocumentReference houseRef,
                                               DocumentReference memberRef, DocumentReference userRef,
                                               String uid, long generation) throws AuthException {
        requireSession(generation, uid);
        return firestore.runTransaction(tx -> {
            requireTransactionSession(generation, uid);
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
            requireTransactionSession(generation, uid);
            if (invite != null && invite.exists() && houseRef.getId().equals(invite.getString(Schema.Invites.HOUSE_ID))) {
                tx.delete(inviteRef);
            }
            tx.delete(memberRef);
            if (house != null) {
                tx.delete(houseRef);
            }
            tx.delete(userRef);
            return null;
        });
    }

    private Task<Session> prepareHouse(FirebaseUser user, long generation) {
        return prepareHouse(user.getUid(),
                HouseholdSetup.fallbackDisplayName(user.getDisplayName(), user.getEmail()), generation);
    }

    /**
     * One transaction, so two devices signing in for the first time cannot create two houses.
     * Transactions need the server, which also keeps setup online-only.
     */
    private Task<Session> prepareHouse(String uid, String nameForNewUser, long generation) {
        FirebaseFirestore firestore = db;
        if (firestore == null) {
            return fail(AuthError.NOT_CONFIGURED);
        }
        if (!isCurrent(generation, uid)) {
            return fail(AuthError.NOT_SIGNED_IN);
        }
        DocumentReference userRef = firestore.collection(Schema.Users.COLLECTION).document(uid);
        return firestore.runTransaction(tx -> {
            requireTransactionSession(generation, uid);
            DocumentSnapshot user = tx.get(userRef);
            String houseId = user.exists() ? user.getString(Schema.Users.CURRENT_HOUSE_ID) : null;
            boolean memberExists = houseId != null && !houseId.isEmpty()
                    && tx.get(memberRef(firestore, houseId, uid)).exists();
            String storedName = user.getString(Schema.Users.DISPLAY_NAME);
            String name = storedName != null ? storedName : nameForNewUser;

            requireTransactionSession(generation, uid);
            switch (HouseholdSetup.decide(user.exists(), houseId, memberExists)) {
                case READY:
                    return new Session(uid, houseId, name, false);
                case CREATE_FIRST_HOUSE: {
                    String newHouseId = createHouse(tx, firestore, uid, name);
                    tx.set(userRef, HouseholdSetup.newUser(newHouseId, name, FieldValue.serverTimestamp()));
                    return new Session(uid, newHouseId, name, false);
                }
                case RECOVER_HOUSE:
                default: {
                    String newHouseId = createHouse(tx, firestore, uid, name);
                    tx.update(userRef, HouseholdSetup.moveUser(newHouseId, FieldValue.serverTimestamp()));
                    return new Session(uid, newHouseId, name, false);
                }
            }
        });
    }

    private static String createHouse(Transaction tx, FirebaseFirestore firestore, String uid, String name) {
        DocumentReference house = firestore.collection(Schema.Houses.COLLECTION).document();
        tx.set(house, HouseholdSetup.newHouse(uid, FieldValue.serverTimestamp()));
        tx.set(memberRef(firestore, house.getId(), uid),
                HouseholdSetup.newMember(name, FieldValue.serverTimestamp()));
        return house.getId();
    }

    private static DocumentReference memberRef(FirebaseFirestore firestore, String houseId, String uid) {
        return firestore.collection(Schema.Houses.COLLECTION).document(houseId)
                .collection(Schema.Members.COLLECTION).document(uid);
    }

    /** Resolves to null when nothing usable is cached. */
    private Task<Session> cachedSession(String uid, long generation) {
        FirebaseFirestore firestore = db;
        if (firestore == null) {
            return Tasks.forResult(null);
        }
        if (!isCurrent(generation, uid)) {
            return fail(AuthError.NOT_SIGNED_IN);
        }
        return firestore.collection(Schema.Users.COLLECTION).document(uid).get(Source.CACHE)
                .continueWith(task -> {
                    requireSession(generation, uid);
                    DocumentSnapshot user = task.isSuccessful() ? task.getResult() : null;
                    String houseId = user == null ? null : user.getString(Schema.Users.CURRENT_HOUSE_ID);
                    String name = user == null ? null : user.getString(Schema.Users.DISPLAY_NAME);
                    if (houseId == null || houseId.isEmpty() || name == null) {
                        return null;
                    }
                    return new Session(uid, houseId, name, true);
                });
    }

    // Already-issued Firebase SDK calls cannot be cancelled. Ignore stale results and do not
    // start another data mutation after an account operation replaces this one.
    private boolean isCurrent(long generation, String uid) {
        FirebaseUser current = auth == null ? null : auth.getCurrentUser();
        return sessions.accepts(generation, uid, current == null ? null : current.getUid());
    }

    private void requireSession(long generation, String uid) throws AuthException {
        if (!isCurrent(generation, uid)) {
            throw new AuthException(AuthError.NOT_SIGNED_IN);
        }
    }

    private void requireTransactionSession(long generation, String uid) {
        if (!isCurrent(generation, uid)) {
            throw new IllegalStateException(new AuthException(AuthError.NOT_SIGNED_IN));
        }
    }

    private Task<Session> sessionResult(Task<Session> task, long generation) {
        return translate(task).continueWithTask(done -> {
            if (!sessions.accepts(generation)) {
                return fail(AuthError.NOT_SIGNED_IN);
            }
            if (done.isSuccessful()) {
                requireSession(generation, done.getResult().uid);
            }
            return done;
        });
    }

    private static <T> Task<T> translate(Task<T> task) {
        return task.continueWithTask(done -> done.isSuccessful()
                ? done
                : Tasks.forException(asAuthException(done.getException())));
    }

    private static AuthException asAuthException(Exception e) {
        return e instanceof AuthException ? (AuthException) e : new AuthException(classify(e), e);
    }

    private static AuthError errorOf(Exception e) {
        return e instanceof AuthException ? ((AuthException) e).error : classify(e);
    }

    static AuthError classify(Throwable e) {
        // Tasks may wrap the real failure (e.g. an exception thrown inside a transaction).
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AuthException) {
                return ((AuthException) t).error;
            }
            if (t instanceof FirebaseNetworkException) {
                return AuthError.NETWORK;
            }
            if (t instanceof FirebaseTooManyRequestsException) {
                return AuthError.TOO_MANY_ATTEMPTS;
            }
            if (t instanceof FirebaseAuthException) {
                return AuthErrorMapper.fromAuthCode(((FirebaseAuthException) t).getErrorCode());
            }
            if (t instanceof FirebaseFirestoreException) {
                return AuthErrorMapper.fromFirestoreCode(((FirebaseFirestoreException) t).getCode().name());
            }
            // Item deletion during account deletion reports ItemException.
            if (t instanceof ItemException) {
                return AuthErrorMapper.fromItemError(((ItemException) t).error.name());
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return AuthError.UNKNOWN;
    }

    private static <T> Task<T> fail(AuthError error) {
        return Tasks.forException(new AuthException(error));
    }
}
