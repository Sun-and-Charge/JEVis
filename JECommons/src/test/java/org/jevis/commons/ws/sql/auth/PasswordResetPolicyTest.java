/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.ws.sql.auth;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class PasswordResetPolicyTest {

    private static PasswordResetPolicy.Entry entry(String code, long now) {
        return new PasswordResetPolicy.Entry(1, 7, PasswordResetPolicy.hash(code), now, PasswordResetPolicy.expiresAt(now), 0, false);
    }

    @Test
    public void generatedCodesHaveSixDigits() {
        Set<String> codes = new HashSet<>();
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 200; i++) {
            String code = PasswordResetPolicy.generateCode(random);
            assertTrue(code, code.matches("\\d{6}"));
            codes.add(code);
        }
        assertTrue("codes must be random", codes.size() > 150);
    }

    @Test
    public void normalizeAcceptsSpacesAndDashes() {
        assertEquals("012345", PasswordResetPolicy.normalize(" 012 345 "));
        assertEquals("012345", PasswordResetPolicy.normalize("012-345"));
        assertNull(PasswordResetPolicy.normalize("12345"));
        assertNull(PasswordResetPolicy.normalize("1234567"));
        assertNull(PasswordResetPolicy.normalize("12a456"));
        assertNull(PasswordResetPolicy.normalize(null));
    }

    @Test
    public void hashDoesNotContainTheCode() {
        String hash = PasswordResetPolicy.hash("123456");
        assertFalse(hash.contains("123456"));
        assertTrue(hash.startsWith("$argon2id$"));
    }

    @Test
    public void correctCodeIsAccepted() {
        long now = 1_000_000L;
        assertEquals(PasswordResetPolicy.Result.OK, PasswordResetPolicy.verify(entry("123456", now), "123 456", now + 1000));
    }

    @Test
    public void wrongCodeIsRejected() {
        long now = 1_000_000L;
        PasswordResetPolicy.Entry entry = entry("123456", now);
        assertEquals(PasswordResetPolicy.Result.WRONG_CODE, PasswordResetPolicy.verify(entry, "654321", now));
        assertEquals(PasswordResetPolicy.Result.WRONG_CODE, PasswordResetPolicy.verify(entry, "abc", now));
        assertEquals(PasswordResetPolicy.Result.WRONG_CODE, PasswordResetPolicy.verify(entry, null, now));
    }

    @Test
    public void codeExpiresAfterThirtyMinutes() {
        long now = 1_000_000L;
        PasswordResetPolicy.Entry entry = entry("123456", now);
        assertEquals(PasswordResetPolicy.Result.OK, PasswordResetPolicy.verify(entry, "123456", now + PasswordResetPolicy.VALIDITY_MS - 1));
        assertEquals(PasswordResetPolicy.Result.EXPIRED, PasswordResetPolicy.verify(entry, "123456", now + PasswordResetPolicy.VALIDITY_MS));
        assertEquals(30 * 60 * 1000L, PasswordResetPolicy.VALIDITY_MS);
    }

    @Test
    public void codeIsBlockedAfterFiveAttempts() {
        long now = 1_000_000L;
        PasswordResetPolicy.Entry entry = entry("123456", now);
        entry.attempts = PasswordResetPolicy.MAX_ATTEMPTS - 1;
        assertEquals(PasswordResetPolicy.Result.OK, PasswordResetPolicy.verify(entry, "123456", now));
        entry.attempts = PasswordResetPolicy.MAX_ATTEMPTS;
        assertEquals(PasswordResetPolicy.Result.TOO_MANY_ATTEMPTS, PasswordResetPolicy.verify(entry, "123456", now));
        assertEquals(5, PasswordResetPolicy.MAX_ATTEMPTS);
    }

    @Test
    public void codeCanBeUsedOnce() {
        long now = 1_000_000L;
        PasswordResetPolicy.Entry entry = entry("123456", now);
        entry.used = true;
        assertEquals(PasswordResetPolicy.Result.USED, PasswordResetPolicy.verify(entry, "123456", now));
    }

    @Test
    public void missingEntry() {
        assertEquals(PasswordResetPolicy.Result.NO_CODE, PasswordResetPolicy.verify(null, "123456", 0));
    }

    @Test
    public void threeRequestsPerHour() {
        assertTrue(PasswordResetPolicy.isRequestAllowed(0));
        assertTrue(PasswordResetPolicy.isRequestAllowed(2));
        assertFalse(PasswordResetPolicy.isRequestAllowed(3));
    }

    @Test
    public void passwordRules() {
        assertFalse(PasswordResetPolicy.isValidPassword(null));
        assertFalse(PasswordResetPolicy.isValidPassword("1234567"));
        assertTrue(PasswordResetPolicy.isValidPassword("12345678"));
    }

    @Test
    public void mailTextContainsCodeAndUser() {
        String text = PasswordResetService.mailText("jdoe", "042137");
        assertTrue(text.contains("042137"));
        assertTrue(text.contains("jdoe"));
        assertTrue(text.contains("Passwort vergessen?"));
        assertTrue(text.contains("Forgot password?"));
    }
}
