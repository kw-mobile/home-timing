package com.kwmobile.hometiming.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Account form rules (docs/MVP.md F-01). The display name is what family members see, so it is
 * entered at sign-up and kept short enough for security rules to bound its size.
 */
public final class AccountInputValidator {
    public static final int NAME_MAX_LENGTH = 20;
    /** Firebase Authentication rejects shorter passwords. */
    public static final int PASSWORD_MIN_LENGTH = 6;

    // Deliberately loose: Firebase is the authority on deliverable addresses.
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    public enum Error {
        NAME_REQUIRED,
        NAME_TOO_LONG,
        EMAIL_INVALID,
        PASSWORD_REQUIRED,
        PASSWORD_TOO_SHORT,
        PASSWORD_MISMATCH
    }

    private AccountInputValidator() {
    }

    public static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim();
    }

    public static Set<Error> validateSignUp(String name, String email, String password, String confirm) {
        EnumSet<Error> errors = EnumSet.noneOf(Error.class);
        String trimmed = normalizeName(name);
        if (trimmed.isEmpty()) {
            errors.add(Error.NAME_REQUIRED);
        } else if (trimmed.codePointCount(0, trimmed.length()) > NAME_MAX_LENGTH) {
            errors.add(Error.NAME_TOO_LONG);
        }
        if (!isEmail(email)) {
            errors.add(Error.EMAIL_INVALID);
        }
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            errors.add(Error.PASSWORD_TOO_SHORT);
        }
        if (password == null || !password.equals(confirm)) {
            errors.add(Error.PASSWORD_MISMATCH);
        }
        return Collections.unmodifiableSet(errors);
    }

    public static Set<Error> validateSignIn(String email, String password) {
        EnumSet<Error> errors = EnumSet.noneOf(Error.class);
        if (!isEmail(email)) {
            errors.add(Error.EMAIL_INVALID);
        }
        if (password == null || password.isEmpty()) {
            errors.add(Error.PASSWORD_REQUIRED);
        }
        return Collections.unmodifiableSet(errors);
    }

    public static Set<Error> validatePasswordReset(String email) {
        return isEmail(email)
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.of(Error.EMAIL_INVALID));
    }

    private static boolean isEmail(String email) {
        return EMAIL.matcher(normalizeEmail(email)).matches();
    }
}
