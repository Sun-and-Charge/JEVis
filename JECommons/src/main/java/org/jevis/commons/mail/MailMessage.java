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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A mail with recipients, text and attachments, sent by {@link MailSender}.
 */
public class MailMessage {

    private final Set<String> to = new LinkedHashSet<>();
    private final Set<String> cc = new LinkedHashSet<>();
    private final Set<String> bcc = new LinkedHashSet<>();
    private final List<Attachment> attachments = new ArrayList<>();
    private String subject = "";
    private String text = "";
    private boolean html;

    public static class Attachment {
        private final String fileName;
        private final byte[] content;
        private final String contentType;

        public Attachment(String fileName, byte[] content, String contentType) {
            this.fileName = fileName;
            this.content = content;
            this.contentType = contentType != null ? contentType : "application/octet-stream";
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getContent() {
            return content;
        }

        public String getContentType() {
            return contentType;
        }
    }

    public MailMessage to(String... addresses) {
        for (String address : addresses) {
            to.addAll(MailRecipients.parseAddresses(address));
        }
        return this;
    }

    public MailMessage to(Iterable<String> addresses) {
        for (String address : addresses) {
            to.addAll(MailRecipients.parseAddresses(address));
        }
        return this;
    }

    public MailMessage cc(Iterable<String> addresses) {
        for (String address : addresses) {
            cc.addAll(MailRecipients.parseAddresses(address));
        }
        return this;
    }

    public MailMessage bcc(Iterable<String> addresses) {
        for (String address : addresses) {
            bcc.addAll(MailRecipients.parseAddresses(address));
        }
        return this;
    }

    public MailMessage subject(String subject) {
        this.subject = subject != null ? subject : "";
        return this;
    }

    public MailMessage text(String text, boolean html) {
        this.text = text != null ? text : "";
        this.html = html;
        return this;
    }

    public MailMessage attach(String fileName, byte[] content, String contentType) {
        attachments.add(new Attachment(fileName, content, contentType));
        return this;
    }

    public Set<String> getTo() {
        return to;
    }

    public Set<String> getCc() {
        return cc;
    }

    public Set<String> getBcc() {
        return bcc;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public String getSubject() {
        return subject;
    }

    public String getText() {
        return text;
    }

    public boolean isHtml() {
        return html;
    }

    public boolean hasRecipients() {
        return !to.isEmpty() || !cc.isEmpty() || !bcc.isEmpty();
    }
}
