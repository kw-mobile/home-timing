package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

import org.junit.Test;

public class InviteCodesTest {
    @Test
    public void generatesSixDigitsIncludingLeadingZeros() {
        Random zero = new Random() {
            @Override
            public int nextInt(int bound) {
                return 42;
            }
        };
        assertEquals("000042", InviteCodes.generate(zero));

        Random seeded = new Random(7);
        for (int i = 0; i < 1000; i++) {
            assertTrue(InviteCodes.isValid(InviteCodes.generate(seeded)));
        }
    }

    @Test
    public void acceptsOnlySixDigits() {
        assertTrue(InviteCodes.isValid("012345"));
        assertFalse(InviteCodes.isValid("12345"));
        assertFalse(InviteCodes.isValid("1234567"));
        assertFalse(InviteCodes.isValid("12a456"));
        assertFalse(InviteCodes.isValid(" 123456"));
        assertFalse(InviteCodes.isValid(null));
    }

    @Test
    public void liveForExactlyTwentyFourHours() {
        Instant created = Instant.parse("2026-11-11T00:00:00Z");

        assertEquals(Instant.parse("2026-11-12T00:00:00Z"), InviteCodes.expiresAt(created));
        assertTrue(InviteCodes.isLive(created, created.plus(Duration.ofHours(24)).minusMillis(1)));
        assertFalse(InviteCodes.isLive(created, created.plus(Duration.ofHours(24))));
    }

    @Test
    public void inviteModelUsesTheSameExpiry() {
        Invite invite = new Invite("123456", "house-a", Instant.parse("2026-11-11T00:00:00Z"));

        assertTrue(invite.isLive(Instant.parse("2026-11-11T23:59:59Z")));
        assertFalse(invite.isLive(Instant.parse("2026-11-12T00:00:00Z")));
    }
}
