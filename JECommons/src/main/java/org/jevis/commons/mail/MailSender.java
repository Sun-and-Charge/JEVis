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
package org.jevis.commons.mail;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.util.ByteArrayDataSource;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;
import java.util.Set;

/**
 * Sends a {@link MailMessage} with the given {@link MailSettings} (javax.mail).
 */
public class MailSender {

    private static final Logger logger = LogManager.getLogger(MailSender.class);
    private static final int TIMEOUT_MS = 30000;

    private final MailSettings settings;

    public MailSender(MailSettings settings) {
        this.settings = settings;
    }

    /**
     * Session properties for the settings; separate from the system properties, so that several settings can be used
     * in one JVM.
     */
    static Properties properties(MailSettings settings) {
        Properties properties = new Properties();
        properties.setProperty("mail.transport.protocol", "smtp");
        properties.setProperty("mail.smtp.host", settings.getHost());
        properties.setProperty("mail.smtp.port", String.valueOf(settings.getPort()));
        properties.setProperty("mail.smtp.auth", String.valueOf(settings.isAuthentication()));
        properties.setProperty("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MS));
        properties.setProperty("mail.smtp.timeout", String.valueOf(TIMEOUT_MS));
        properties.setProperty("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MS));
        switch (settings.getSecurity()) {
            case STARTTLS:
                properties.setProperty("mail.smtp.starttls.enable", "true");
                break;
            case SSL:
                properties.setProperty("mail.smtp.ssl.enable", "true");
                break;
            default:
                break;
        }
        return properties;
    }

    /**
     * Builds the message without sending it.
     */
    MimeMessage build(MailMessage mail, Session session) throws MessagingException {
        MimeMessage message = new MimeMessage(session);
        String from = settings.getFrom();
        if (from != null && !from.isEmpty()) {
            message.setFrom(new InternetAddress(from));
        }
        setRecipients(message, Message.RecipientType.TO, mail.getTo());
        setRecipients(message, Message.RecipientType.CC, mail.getCc());
        setRecipients(message, Message.RecipientType.BCC, mail.getBcc());
        message.setSubject(mail.getSubject(), StandardCharsets.UTF_8.name());
        message.setSentDate(new Date());

        MimeBodyPart body = new MimeBodyPart();
        if (mail.isHtml()) {
            body.setContent(mail.getText(), "text/html; charset=UTF-8");
        } else {
            body.setText(mail.getText(), StandardCharsets.UTF_8.name());
        }

        if (mail.getAttachments().isEmpty()) {
            message.setContent(new MimeMultipart(body));
        } else {
            MimeMultipart multipart = new MimeMultipart();
            multipart.addBodyPart(body);
            for (MailMessage.Attachment attachment : mail.getAttachments()) {
                MimeBodyPart part = new MimeBodyPart();
                part.setDataHandler(new DataHandler(new ByteArrayDataSource(attachment.getContent(), attachment.getContentType())));
                try {
                    part.setFileName(MimeUtility.encodeText(attachment.getFileName(), StandardCharsets.UTF_8.name(), null));
                } catch (java.io.UnsupportedEncodingException e) {
                    part.setFileName(attachment.getFileName());
                }
                multipart.addBodyPart(part);
            }
            message.setContent(multipart);
        }
        message.saveChanges();
        return message;
    }

    /**
     * Sends the mail.
     *
     * @throws MessagingException if the settings are incomplete, there are no recipients or the server rejects it
     */
    public void send(MailMessage mail) throws MessagingException {
        if (settings == null || !settings.isValid()) {
            throw new MessagingException("No SMTP server configured");
        }
        if (!mail.hasRecipients()) {
            throw new MessagingException("No recipients");
        }
        Authenticator authenticator = null;
        if (settings.isAuthentication()) {
            authenticator = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(settings.getUser(), settings.getPassword());
                }
            };
        }
        Session session = Session.getInstance(properties(settings), authenticator);
        MimeMessage message = build(mail, session);
        Transport.send(message);
        logger.info("Mail '{}' sent to {} recipient(s)", mail.getSubject(), mail.getTo().size() + mail.getCc().size() + mail.getBcc().size());
    }

    private static void setRecipients(MimeMessage message, Message.RecipientType type, Set<String> addresses) throws MessagingException {
        if (addresses.isEmpty()) {
            return;
        }
        InternetAddress[] internetAddresses = new InternetAddress[addresses.size()];
        int i = 0;
        for (String address : addresses) {
            internetAddresses[i++] = new InternetAddress(address, true);
        }
        message.setRecipients(type, internetAddresses);
    }
}
