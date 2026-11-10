package com.kwmobile.hometiming.data.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
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
import com.kwmobile.hometiming.data.firestore.HouseholdSetup;
import com.kwmobile.hometiming.data.firestore.Schema;
import com.kwmobile.hometiming.domain.AccountInputValidator;

/**
 * Email accounts (docs/MVP.md F-01) and the one-person house every signed-in user belongs to.
 *
 * <p>Every result is a {@link Task}; failures are {@link AuthException}s whose {@link AuthError}
 * selects the message. Screens validate input with {@link AccountInputValidator} first; this class
 * checks again and reports {@link AuthError#INVALID_INPUT} instead of calling Firebase.
 */
public final class AuthRepository {
    private static volatile AuthRepository instance;

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
        Task<Session> task = auth.createUserWithEmailAndPassword(address, password)
                .onSuccessTask(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        return Tasks.forException(new AuthException(AuthError.UNKNOWN));
                    }
                    UserProfileChangeRequest profile = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name).build();
                    // The house uses the typed name even if the profile update fails.
                    return user.updateProfile(profile)
                            .continueWithTask(ignored -> prepareHouse(user.getUid(), name));
                });
        return translate(task);
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
        Task<Session> task = auth.signInWithEmailAndPassword(address, password)
                .onSuccessTask(result -> {
                    FirebaseUser user = result.getUser();
                    return user == null
                            ? Tasks.forException(new AuthException(AuthError.UNKNOWN))
                            : prepareHouse(user);
                });
        return translate(task);
    }

    /**
     * Restores the stored session at app start. Online, it also creates or recovers the house. When
     * the server is unreachable it falls back to the cached user document with {@code offline = true}.
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
        return translate(prepareHouse(user)).continueWithTask(task -> {
            if (task.isSuccessful() || errorOf(task.getException()) != AuthError.NETWORK) {
                return task;
            }
            return cachedSession(user.getUid()).continueWithTask(cached ->
                    cached.isSuccessful() && cached.getResult() != null ? cached : task);
        });
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
        if (auth != null) {
            auth.signOut();
        }
    }

    private Task<Session> prepareHouse(FirebaseUser user) {
        return prepareHouse(user.getUid(),
                HouseholdSetup.fallbackDisplayName(user.getDisplayName(), user.getEmail()));
    }

    /**
     * One transaction, so two devices signing in for the first time cannot create two houses.
     * Transactions need the server, which also keeps setup online-only.
     */
    private Task<Session> prepareHouse(String uid, String nameForNewUser) {
        FirebaseFirestore firestore = db;
        if (firestore == null) {
            return fail(AuthError.NOT_CONFIGURED);
        }
        DocumentReference userRef = firestore.collection(Schema.Users.COLLECTION).document(uid);
        return firestore.runTransaction(tx -> {
            DocumentSnapshot user = tx.get(userRef);
            String houseId = user.exists() ? user.getString(Schema.Users.CURRENT_HOUSE_ID) : null;
            boolean memberExists = houseId != null && !houseId.isEmpty()
                    && tx.get(memberRef(firestore, houseId, uid)).exists();
            String storedName = user.getString(Schema.Users.DISPLAY_NAME);
            String name = storedName != null ? storedName : nameForNewUser;

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
    private Task<Session> cachedSession(String uid) {
        FirebaseFirestore firestore = db;
        if (firestore == null) {
            return Tasks.forResult(null);
        }
        return firestore.collection(Schema.Users.COLLECTION).document(uid).get(Source.CACHE)
                .continueWith(task -> {
                    DocumentSnapshot user = task.isSuccessful() ? task.getResult() : null;
                    String houseId = user == null ? null : user.getString(Schema.Users.CURRENT_HOUSE_ID);
                    String name = user == null ? null : user.getString(Schema.Users.DISPLAY_NAME);
                    if (houseId == null || houseId.isEmpty() || name == null) {
                        return null;
                    }
                    return new Session(uid, houseId, name, true);
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
