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

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * Rules of the self-service password reset (OpenJEVis #2267), without database access.
 * <p>
 * A reset code has six digits, only its Argon2id hash is stored. It is valid for 30 minutes, allows five wrong
 * attempts and can be used once. A user can request at most three codes per hour.
 */
public final class PasswordResetPolicy {

    public static final int CODE_LENGTH = 6;
    public static final long VALIDITY_MS = TimeUnit.MINUTES.toMillis(30);
    public static final int MAX_ATTEMPTS = 5;
    public static final int MAX_REQUESTS_PER_WINDOW = 3;
    public static final long REQUEST_WINDOW_MS = TimeUnit.HOURS.toMillis(1);
    public static final int MIN_PASSWORD_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Result {
        OK, WRONG_CODE, EXPIRED, USED, TOO_MANY_ATTEMPTS, NO_CODE
    }

    /**
     * A stored reset code.
     */
    public static class Entry {
        public long id;
        public long userId;
        public String codeHash;
        public long created;
        public long expires;
        public int attempts;
        public boolean used;

        public Entry() {
        }

        public Entry(long id, long userId, String codeHash, long created, long expires, int attempts, boolean used) {
            this.id = id;
            this.userId = userId;
            this.codeHash = codeHash;
            this.created = created;
            this.expires = expires;
            this.attempts = attempts;
            this.used = used;
        }
    }

    private PasswordResetPolicy() {
    }

    /**
     * @return a new random code with {@value #CODE_LENGTH} digits, leading zeros included
     */
    public static String generateCode() {
        return generateCode(RANDOM);
    }

    static String generateCode(SecureRandom random) {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    /**
     * Removes spaces and dashes the user may have typed.
     *
     * @return the six digits or {@code null} if the input is not a code
     */
    public static String normalize(String code) {
        if (code == null) return null;
        String digits = code.replaceAll("[\\s-]", "");
        return digits.matches("\\d{" + CODE_LENGTH + "}") ? digits : null;
    }

    public static String hash(String code) {
        return PasswordHasher.hash(code);
    }

    public static long expiresAt(long createdMs) {
        return createdMs + VALIDITY_MS;
    }

    /**
     * Checks a code against a stored entry. The caller counts a {@link Result#WRONG_CODE} as attempt and marks the
     * entry as used on {@link Result#OK}.
     */
    public static Result verify(Entry entry, String code, long nowMs) {
        if (entry == null) return Result.NO_CODE;
        if (entry.used) return Result.USED;
        if (nowMs >= entry.expires) return Result.EXPIRED;
        if (entry.attempts >= MAX_ATTEMPTS) return Result.TOO_MANY_ATTEMPTS;
        String normalized = normalize(code);
        if (normalized == null) return Result.WRONG_CODE;
        return PasswordHasher.verify(normalized, entry.codeHash) ? Result.OK : Result.WRONG_CODE;
    }

    /**
     * @param requestsInWindow codes requested by the user in the last {@link #REQUEST_WINDOW_MS}
     */
    public static boolean isRequestAllowed(int requestsInWindow) {
        return requestsInWindow < MAX_REQUESTS_PER_WINDOW;
    }

    /**
     * Same rule as the password change of a logged-in user.
     */
    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }
}
