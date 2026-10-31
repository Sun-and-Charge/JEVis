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

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link MailSender} that sets a Reply-To header, e.g. the address of the user who sends a mail through the
 * webservice while the sender is the system mail account.
 */
public class ReplyToMailSender extends MailSender {

    private final List<String> replyTo;

    /**
     * @param replyTo address list (';' or ',' separated), may be null or empty
     */
    public ReplyToMailSender(MailSettings settings, String replyTo) {
        super(settings);
        this.replyTo = MailRecipients.parseAddresses(replyTo);
    }

    @Override
    MimeMessage build(MailMessage mail, Session session) throws MessagingException {
        MimeMessage message = super.build(mail, session);
        if (!replyTo.isEmpty()) {
            List<InternetAddress> addresses = new ArrayList<>();
            for (String address : replyTo) {
                addresses.add(new InternetAddress(address, true));
            }
            message.setReplyTo(addresses.toArray(new InternetAddress[0]));
            message.saveChanges();
        }
        return message;
    }
}
