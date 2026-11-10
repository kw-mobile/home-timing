package com.kwmobile.hometiming.data.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class AuthErrorMapperTest {
    @Test
    public void mapsAuthErrorCodes() {
        assertEquals(AuthError.INVALID_EMAIL, AuthErrorMapper.fromAuthCode("ERROR_INVALID_EMAIL"));
        assertEquals(AuthError.WEAK_PASSWORD, AuthErrorMapper.fromAuthCode("ERROR_WEAK_PASSWORD"));
        assertEquals(AuthError.EMAIL_IN_USE, AuthErrorMapper.fromAuthCode("ERROR_EMAIL_ALREADY_IN_USE"));
        assertEquals(AuthError.USER_DISABLED, AuthErrorMapper.fromAuthCode("ERROR_USER_DISABLED"));
        assertEquals(AuthError.TOO_MANY_ATTEMPTS, AuthErrorMapper.fromAuthCode("ERROR_TOO_MANY_REQUESTS"));
        assertEquals(AuthError.NETWORK, AuthErrorMapper.fromAuthCode("ERROR_NETWORK_REQUEST_FAILED"));
    }

    @Test
    public void unknownUserAndWrongPasswordLookTheSame() {
        assertEquals(AuthError.WRONG_CREDENTIALS, AuthErrorMapper.fromAuthCode("ERROR_WRONG_PASSWORD"));
        assertEquals(AuthError.WRONG_CREDENTIALS, AuthErrorMapper.fromAuthCode("ERROR_USER_NOT_FOUND"));
        assertEquals(AuthError.WRONG_CREDENTIALS, AuthErrorMapper.fromAuthCode("ERROR_INVALID_CREDENTIAL"));
    }

    @Test
    public void mapsFirestoreCodes() {
        assertEquals(AuthError.NETWORK, AuthErrorMapper.fromFirestoreCode("UNAVAILABLE"));
        assertEquals(AuthError.NETWORK, AuthErrorMapper.fromFirestoreCode("DEADLINE_EXCEEDED"));
        assertEquals(AuthError.PERMISSION_DENIED, AuthErrorMapper.fromFirestoreCode("PERMISSION_DENIED"));
        assertEquals(AuthError.PERMISSION_DENIED, AuthErrorMapper.fromFirestoreCode("UNAUTHENTICATED"));
    }

    @Test
    public void unknownCodesAreUnknown() {
        assertEquals(AuthError.UNKNOWN, AuthErrorMapper.fromAuthCode("ERROR_SOMETHING_NEW"));
        assertEquals(AuthError.UNKNOWN, AuthErrorMapper.fromAuthCode(null));
        assertEquals(AuthError.UNKNOWN, AuthErrorMapper.fromFirestoreCode("INTERNAL"));
        assertEquals(AuthError.UNKNOWN, AuthErrorMapper.fromFirestoreCode(null));
    }

    @Test
    public void everyErrorHasItsOwnMessage() {
        Set<Integer> messages = new HashSet<>();
        for (AuthError error : AuthError.values()) {
            assertNotEquals(error.name(), 0, error.messageRes);
            messages.add(error.messageRes);
        }
        assertEquals(AuthError.values().length, messages.size());
    }
}
