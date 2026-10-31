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
package org.jevis.commons.ws.sql.mail;

import org.jevis.commons.mail.MailMessage;
import org.jevis.commons.mail.MailRecipients;
import org.jevis.commons.ws.json.JsonMailRequest;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.*;

/**
 * Checks and converts a {@link JsonMailRequest} (OpenJEVis #2234), without database access.
 * <p>
 * Limits: at most {@value #MAX_RECIPIENTS} recipients, at most {@value #MAX_ATTACHMENT_BYTES} bytes of attachments
 * in total and {@value #MAX_ATTACHMENTS} attachments.
 */
public final class MailRequestValidator {

    public static final int MAX_RECIPIENTS = 50;
    public static final long MAX_ATTACHMENT_BYTES = 15L * 1024 * 1024;
    public static final int MAX_ATTACHMENTS = 20;
    public static final int MAX_SUBJECT_LENGTH = 255;
    public static final int MAX_TEXT_LENGTH = 100_000;
    /**
     * Upper bound of the request body: Base64 needs 4/3 of the attachment size, plus text and JSON overhead.
     */
    public static final long MAX_BODY_CHARS = MAX_ATTACHMENT_BYTES / 3 * 4 + 1024 * 1024;

    private MailRequestValidator() {
    }

    /**
     * Thrown for requests that break a rule; the message can be shown to the user.
     */
    public static class InvalidMailRequestException extends Exception {
        public InvalidMailRequestException(String message) {
            super(message);
        }
    }

    /**
     * @return the explicitly given addresses, split and without duplicates
     * @throws InvalidMailRequestException if an address is not valid
     */
    public static Set<String> explicitAddresses(JsonMailRequest request) throws InvalidMailRequestException {
        Set<String> addresses = new LinkedHashSet<>();
        if (request.getTo() != null) {
            for (String entry : request.getTo()) {
                for (String address : MailRecipients.parseAddresses(entry)) {
                    checkAddress(address);
                    addresses.add(address);
                }
            }
        }
        return addresses;
    }

    public static void checkAddress(String address) throws InvalidMailRequestException {
        try {
            InternetAddress internetAddress = new InternetAddress(address, true);
            internetAddress.validate();
            if (!address.contains("@")) throw new AddressException("missing @");
        } catch (AddressException e) {
            throw new InvalidMailRequestException("Invalid e-mail address: " + address);
        }
    }

    /**
     * Checks the number of recipients after users and groups were resolved.
     */
    public static void checkRecipients(Collection<String> recipients) throws InvalidMailRequestException {
        if (recipients == null || recipients.isEmpty()) {
            throw new InvalidMailRequestException("No recipients with an e-mail address");
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            throw new InvalidMailRequestException("Too many recipients (" + recipients.size() + "), at most " + MAX_RECIPIENTS + " are allowed");
        }
    }

    /**
     * Builds the mail: checks subject, text and attachments and decodes the attachments.
     *
     * @param recipients the resolved recipients, see {@link #checkRecipients(Collection)}
     */
    public static MailMessage toMessage(JsonMailRequest request, Collection<String> recipients) throws InvalidMailRequestException {
        return toMessage(request, recipients, Collections.emptyList());
    }

    /**
     * @param to  addresses typed by the sender, visible to all recipients
     * @param bcc addresses resolved from users and groups; sent as blind copy so that group members do not see each
     *            other's addresses
     */
    public static MailMessage toMessage(JsonMailRequest request, Collection<String> to, Collection<String> bcc) throws InvalidMailRequestException {
        Set<String> recipients = new LinkedHashSet<>(to);
        recipients.addAll(bcc);
        checkRecipients(recipients);
        String subject = request.getSubject() != null ? request.getSubject().replaceAll("[\\r\\n]+", " ").trim() : "";
        if (subject.isEmpty()) {
            throw new InvalidMailRequestException("The subject is missing");
        }
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            throw new InvalidMailRequestException("The subject is too long");
        }
        String text = request.getText() != null ? request.getText() : "";
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new InvalidMailRequestException("The text is too long");
        }

        Set<String> blind = new LinkedHashSet<>(bcc);
        blind.removeAll(to);
        MailMessage message = new MailMessage().to(to).bcc(blind).subject(subject).text(text, false);
        List<JsonMailRequest.Attachment> attachments = request.getAttachments() != null ? request.getAttachments() : Collections.emptyList();
        if (attachments.size() > MAX_ATTACHMENTS) {
            throw new InvalidMailRequestException("Too many attachments, at most " + MAX_ATTACHMENTS + " are allowed");
        }
        long total = 0;
        for (JsonMailRequest.Attachment attachment : attachments) {
            if (attachment == null || attachment.getData() == null) {
                throw new InvalidMailRequestException("Attachment without content");
            }
            byte[] content;
            try {
                content = Base64.getDecoder().decode(attachment.getData().trim());
            } catch (IllegalArgumentException e) {
                throw new InvalidMailRequestException("Attachment is not Base64 encoded");
            }
            total += content.length;
            if (total > MAX_ATTACHMENT_BYTES) {
                throw new InvalidMailRequestException("The attachments are larger than " + (MAX_ATTACHMENT_BYTES / 1024 / 1024) + " MB");
            }
            message.attach(safeFileName(attachment.getFileName()), content, safeContentType(attachment.getContentType()));
        }
        return message;
    }

    /**
     * Removes path parts and control characters of a file name.
     */
    public static String safeFileName(String fileName) {
        if (fileName == null) return "attachment";
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>:|?*]", "_").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return "attachment";
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    static String safeContentType(String contentType) {
        if (contentType == null || !contentType.matches("[\\w.+-]+/[\\w.+-]+")) {
            return "application/octet-stream";
        }
        return contentType;
    }

    public static long attachmentBytes(MailMessage message) {
        long total = 0;
        for (MailMessage.Attachment attachment : message.getAttachments()) {
            total += attachment.getContent().length;
        }
        return total;
    }
}
