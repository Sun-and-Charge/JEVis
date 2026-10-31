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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.mail.MailMessage;
import org.jevis.commons.mail.MailSender;
import org.jevis.commons.mail.MailSettings;
import org.jevis.commons.ws.sql.CachedAccessControl;
import org.jevis.commons.ws.sql.JEVisUserSQL;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.mail.ServerMail;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Self-service password reset with a code sent by mail (OpenJEVis #2267).
 *
 * <ol>
 *     <li>{@link #requestReset}: the user enters the user name or e-mail address. If an enabled user with an
 *     e-mail address matches, a code is stored (hashed) and mailed. The caller always answers the same way.</li>
 *     <li>{@link #resetPassword}: the code and the new password set the password, sign out all sessions and clear
 *     the lockout.</li>
 * </ol>
 * The mail is sent in the background, so the response time does not reveal whether the user exists.
 */
public final class PasswordResetService {

    public static final String SUBJECT = "JEVis – Passwort zurücksetzen / Password reset";
    private static final Logger logger = LogManager.getLogger(PasswordResetService.class);
    private static final ExecutorService MAIL_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "password-reset-mail");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * Used to spend the same hashing time for unknown users.
     */
    private static final String DUMMY_HASH = PasswordHasher.hash("000000");

    private PasswordResetService() {
    }

    private static PasswordResetStore store(SQLDataSource ds) throws SQLException {
        return new PasswordResetStore(ds.getConnection());
    }

    /**
     * @return {@code true} if the auth tables (V10) and the reset table (V13) exist
     */
    public static boolean isAvailable(SQLDataSource ds) {
        try {
            return AuthService.isAvailable(ds) && store(ds).isSchemaAvailable();
        } catch (SQLException ex) {
            logger.error("Could not check the password reset table", ex);
            return false;
        }
    }

    /**
     * Finds the users for a login: the account name, or else all users whose e-mail address matches.
     */
    static List<JEVisUserSQL> findUsers(SQLDataSource ds, String login) throws Exception {
        List<JEVisUserSQL> result = new ArrayList<>();
        if (login == null || login.trim().isEmpty()) return result;
        String value = login.trim();
        CachedAccessControl cac = CachedAccessControl.getInstance(ds, false);
        JEVisUserSQL byName = cac.getUser(value);
        if (byName != null) {
            result.add(byName);
            return result;
        }
        if (value.contains("@")) {
            for (JEVisUserSQL user : new ArrayList<>(cac.getUsers().values())) {
                for (String address : ServerMail.addressesOfUser(ds, user.getUserID())) {
                    if (address.equalsIgnoreCase(value)) {
                        result.add(user);
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Creates and mails a reset code if the login matches an enabled user with an e-mail address. Never reveals
     * whether that is the case; problems are only logged.
     */
    public static void requestReset(SQLDataSource ds, String login, String clientInfo) {
        try {
            if (!isAvailable(ds)) {
                logger.warn("Password reset requested, but the reset is not available (migration V10/V13 missing)");
                return;
            }
            PasswordResetStore store = store(ds);
            long now = System.currentTimeMillis();
            store.deleteOlderThan(now - TimeUnit.DAYS.toMillis(1));

            /* the mail server is checked per user below, so the answer does not depend on it */
            MailSettings settings = ServerMail.findDefaultSettings(ds);
            List<JEVisUserSQL> users = findUsers(ds, login);
            boolean handled = false;
            for (JEVisUserSQL user : users) {
                if (!user.isEnabled() || !ServerMail.isEnabled(ds, user.getUserID())) {
                    logger.warn("Password reset requested for disabled user '{}', ignored", user.getAccountName());
                    continue;
                }
                List<String> addresses = ServerMail.addressesOfUser(ds, user.getUserID());
                if (addresses.isEmpty()) {
                    logger.warn("Password reset requested for user '{}' without e-mail address, ignored", user.getAccountName());
                    continue;
                }
                if (!PasswordResetPolicy.isRequestAllowed(store.countRequestsSince(user.getUserID(), now - PasswordResetPolicy.REQUEST_WINDOW_MS))) {
                    logger.warn("Too many password reset requests for user '{}' from {}, ignored", user.getAccountName(), clientInfo);
                    continue;
                }
                if (settings == null || !settings.isValid()) {
                    logger.warn("Password reset requested for user '{}', but no mail server is configured (EMail Plugin marked as Default)", user.getAccountName());
                    continue;
                }
                String code = PasswordResetPolicy.generateCode();
                store.invalidateOpen(user.getUserID());
                long id = store.insert(user.getUserID(), PasswordResetPolicy.hash(code), now, PasswordResetPolicy.expiresAt(now), clientInfo);
                handled = true;
                logger.info("Password reset code created for user '{}' (request from {})", user.getAccountName(), clientInfo);
                sendMail(settings, addresses, user.getAccountName(), code, id);
            }
            if (!handled) {
                /* same hashing work as for a known user */
                PasswordHasher.verify("000000", DUMMY_HASH);
                if (users.isEmpty()) {
                    logger.info("Password reset requested for unknown login from {}", clientInfo);
                }
            }
        } catch (Exception ex) {
            logger.error("Password reset request failed", ex);
        }
    }

    private static void sendMail(MailSettings settings, List<String> addresses, String accountName, String code, long resetId) {
        MailMessage message = new MailMessage().to(addresses).subject(SUBJECT).text(mailText(accountName, code), false);
        MAIL_EXECUTOR.submit(() -> {
            try {
                new MailSender(settings).send(message);
                logger.info("Password reset mail sent for user '{}'", accountName);
            } catch (Exception ex) {
                /* the code stays valid, but nobody can know it; it expires */
                logger.error("Could not send the password reset mail for user '{}' (reset id {}): {}", accountName, resetId, ex.getMessage());
            }
        });
    }

    static String mailText(String accountName, String code) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(PasswordResetPolicy.VALIDITY_MS);
        return "Hallo,\n\n"
                + "für den JEVis-Benutzer \"" + accountName + "\" wurde das Zurücksetzen des Passworts angefordert.\n"
                + "Ihr Code lautet: " + code + "\n\n"
                + "Geben Sie den Code im Anmeldedialog unter \"Passwort vergessen?\" ein. Er ist " + minutes
                + " Minuten gültig und kann nur einmal verwendet werden.\n"
                + "Wenn Sie das nicht angefordert haben, ignorieren Sie diese E-Mail; Ihr Passwort bleibt unverändert.\n\n"
                + "----\n\n"
                + "Hello,\n\n"
                + "a password reset was requested for the JEVis user \"" + accountName + "\".\n"
                + "Your code is: " + code + "\n\n"
                + "Enter the code in the login dialog under \"Forgot password?\". It is valid for " + minutes
                + " minutes and can be used once.\n"
                + "If you did not request this, ignore this e-mail; your password stays unchanged.\n";
    }

    /**
     * Sets a new password with a reset code.
     *
     * @return {@code true} if the code was valid and the password was changed
     */
    public static boolean resetPassword(SQLDataSource ds, String login, String code, String newPassword, String clientInfo) throws Exception {
        if (!isAvailable(ds) || !PasswordResetPolicy.isValidPassword(newPassword)) return false;
        PasswordResetStore store = store(ds);
        long now = System.currentTimeMillis();
        for (JEVisUserSQL user : findUsers(ds, login)) {
            PasswordResetPolicy.Entry entry = store.findOpen(user.getUserID());
            PasswordResetPolicy.Result result = PasswordResetPolicy.verify(entry, code, now);
            switch (result) {
                case OK:
                    if (!user.isEnabled() || !store.markUsed(entry.id)) {
                        logger.warn("Password reset for user '{}' rejected (disabled or code already used)", user.getAccountName());
                        continue;
                    }
                    /* stores the new hash, clears failed_attempts, locked_until and must_change, signs out all sessions */
                    AuthService.setPassword(ds, user.getUserID(), newPassword);
                    CachedAccessControl.getInstance(ds, false).invalidate();
                    logger.info("Password of user '{}' was reset with a mailed code (request from {})", user.getAccountName(), clientInfo);
                    return true;
                case WRONG_CODE:
                    store.recordAttempt(entry.id);
                    if (entry.attempts + 1 >= PasswordResetPolicy.MAX_ATTEMPTS) {
                        store.invalidate(entry.id);
                        logger.warn("Password reset code of user '{}' invalidated after {} wrong attempts", user.getAccountName(), PasswordResetPolicy.MAX_ATTEMPTS);
                    } else {
                        logger.warn("Wrong password reset code for user '{}' from {}", user.getAccountName(), clientInfo);
                    }
                    break;
                case NO_CODE:
                    break;
                default:
                    logger.warn("Password reset for user '{}' rejected: {}", user.getAccountName(), result);
                    break;
            }
        }
        return false;
    }
}
