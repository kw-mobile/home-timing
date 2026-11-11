package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.AccountInputValidator.Error;

import java.util.EnumSet;

import org.junit.Test;

public class AccountInputValidatorTest {
    @Test
    public void acceptsValidSignUp() {
        assertTrue(AccountInputValidator.validateSignUp("민지", "minji@kw.ac.kr", "secret1", "secret1").isEmpty());
    }

    @Test
    public void trimsNameAndEmailBeforeChecking() {
        assertTrue(AccountInputValidator.validateSignUp("  민지  ", " minji@kw.ac.kr ", "secret1", "secret1").isEmpty());
        assertEquals("민지", AccountInputValidator.normalizeName("  민지  "));
        assertEquals("minji@kw.ac.kr", AccountInputValidator.normalizeEmail(" minji@kw.ac.kr "));
    }

    @Test
    public void nameIsRequiredAndAtMostTwentyCharacters() {
        assertEquals(EnumSet.of(Error.NAME_REQUIRED),
                AccountInputValidator.validateSignUp("   ", "a@b.co", "secret1", "secret1"));
        assertEquals(EnumSet.of(Error.NAME_REQUIRED),
                AccountInputValidator.validateSignUp(null, "a@b.co", "secret1", "secret1"));
        assertTrue(AccountInputValidator.validateSignUp("가".repeat(20), "a@b.co", "secret1", "secret1").isEmpty());
        assertEquals(EnumSet.of(Error.NAME_TOO_LONG),
                AccountInputValidator.validateSignUp("가".repeat(21), "a@b.co", "secret1", "secret1"));
    }

    @Test
    public void nameLengthCountsCharactersNotUtf16Units() {
        // 20 emoji are 40 UTF-16 units but 20 characters.
        assertTrue(AccountInputValidator.validateSignUp("😀".repeat(20), "a@b.co", "secret1", "secret1").isEmpty());
    }

    @Test
    public void passwordNeedsSixCharactersAndMatchingConfirmation() {
        assertEquals(EnumSet.of(Error.PASSWORD_TOO_SHORT),
                AccountInputValidator.validateSignUp("민지", "a@b.co", "12345", "12345"));
        assertTrue(AccountInputValidator.validateSignUp("민지", "a@b.co", "123456", "123456").isEmpty());
        assertEquals(EnumSet.of(Error.PASSWORD_MISMATCH),
                AccountInputValidator.validateSignUp("민지", "a@b.co", "secret1", "secret2"));
    }

    @Test
    public void rejectsMalformedEmails() {
        for (String email : new String[] {"", "minji", "minji@", "@kw.ac.kr", "minji@kw", "mi nji@kw.ac.kr", null}) {
            assertEquals("email " + email, EnumSet.of(Error.EMAIL_INVALID),
                    AccountInputValidator.validatePasswordReset(email));
        }
    }

    @Test
    public void signInNeedsEmailAndAnyPassword() {
        assertTrue(AccountInputValidator.validateSignIn("a@b.co", "x").isEmpty());
        assertEquals(EnumSet.of(Error.EMAIL_INVALID, Error.PASSWORD_REQUIRED),
                AccountInputValidator.validateSignIn("nope", ""));
    }
}
