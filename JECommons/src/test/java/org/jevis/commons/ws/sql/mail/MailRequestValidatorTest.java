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
import org.jevis.commons.ws.json.JsonMailRequest;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class MailRequestValidatorTest {

    private static JsonMailRequest request(String... to) {
        JsonMailRequest request = new JsonMailRequest();
        request.setTo(new ArrayList<>(Arrays.asList(to)));
        request.setSubject("Analysis");
        request.setText("See attachment");
        return request;
    }

    private static String base64(int size) {
        return Base64.getEncoder().encodeToString(new byte[size]);
    }

    @Test
    public void explicitAddressesAreSplitAndDeduplicated() throws Exception {
        Set<String> addresses = MailRequestValidator.explicitAddresses(request("a@example.com; b@example.com", "a@example.com"));
        assertEquals(new LinkedHashSet<>(Arrays.asList("a@example.com", "b@example.com")), addresses);
    }

    @Test(expected = MailRequestValidator.InvalidMailRequestException.class)
    public void invalidAddressIsRejected() throws Exception {
        MailRequestValidator.explicitAddresses(request("no-address"));
    }

    @Test(expected = MailRequestValidator.InvalidMailRequestException.class)
    public void noRecipients() throws Exception {
        MailRequestValidator.checkRecipients(Collections.emptySet());
    }

    @Test
    public void recipientLimit() throws Exception {
        Set<String> recipients = new LinkedHashSet<>();
        for (int i = 0; i < MailRequestValidator.MAX_RECIPIENTS; i++) {
            recipients.add("user" + i + "@example.com");
        }
        MailRequestValidator.checkRecipients(recipients);
        recipients.add("one-more@example.com");
        try {
            MailRequestValidator.checkRecipients(recipients);
            fail("51 recipients must be rejected");
        } catch (MailRequestValidator.InvalidMailRequestException expected) {
            assertTrue(expected.getMessage().contains("50"));
        }
    }

    @Test
    public void messageWithAttachment() throws Exception {
        JsonMailRequest request = request("a@example.com");
        request.getAttachments().add(new JsonMailRequest.Attachment("../../evil\\report.pdf", "application/pdf", base64(1000)));
        MailMessage message = MailRequestValidator.toMessage(request, Collections.singleton("a@example.com"));
        assertEquals("Analysis", message.getSubject());
        assertEquals(1, message.getAttachments().size());
        assertEquals("report.pdf", message.getAttachments().get(0).getFileName());
        assertEquals(1000, message.getAttachments().get(0).getContent().length);
        assertEquals(1000, MailRequestValidator.attachmentBytes(message));
    }

    @Test
    public void usersAndGroupsAreBlindCopies() throws Exception {
        JsonMailRequest request = request("a@example.com");
        MailMessage message = MailRequestValidator.toMessage(request, Collections.singleton("a@example.com"),
                java.util.Arrays.asList("a@example.com", "member1@example.com", "member2@example.com"));
        assertEquals(Collections.singleton("a@example.com"), message.getTo());
        assertEquals(2, message.getBcc().size());
        assertTrue(message.getBcc().contains("member1@example.com"));
    }

    @Test
    public void attachmentsUpTo15MB() throws Exception {
        JsonMailRequest request = request("a@example.com");
        request.getAttachments().add(new JsonMailRequest.Attachment("a.png", "image/png", base64((int) MailRequestValidator.MAX_ATTACHMENT_BYTES - 10)));
        request.getAttachments().add(new JsonMailRequest.Attachment("b.png", "image/png", base64(10)));
        MailRequestValidator.toMessage(request, Collections.singleton("a@example.com"));

        request.getAttachments().add(new JsonMailRequest.Attachment("c.png", "image/png", base64(1)));
        try {
            MailRequestValidator.toMessage(request, Collections.singleton("a@example.com"));
            fail("more than 15 MB must be rejected");
        } catch (MailRequestValidator.InvalidMailRequestException expected) {
            assertTrue(expected.getMessage().contains("15 MB"));
        }
    }

    @Test(expected = MailRequestValidator.InvalidMailRequestException.class)
    public void invalidBase64() throws Exception {
        JsonMailRequest request = request("a@example.com");
        request.getAttachments().add(new JsonMailRequest.Attachment("a.png", "image/png", "not base64 !!!"));
        MailRequestValidator.toMessage(request, Collections.singleton("a@example.com"));
    }

    @Test(expected = MailRequestValidator.InvalidMailRequestException.class)
    public void subjectIsRequired() throws Exception {
        JsonMailRequest request = request("a@example.com");
        request.setSubject("  ");
        MailRequestValidator.toMessage(request, Collections.singleton("a@example.com"));
    }

    @Test
    public void subjectWithoutLineBreaks() throws Exception {
        JsonMailRequest request = request("a@example.com");
        request.setSubject("Line 1\r\nBcc: x@example.com");
        MailMessage message = MailRequestValidator.toMessage(request, Collections.singleton("a@example.com"));
        assertFalse(message.getSubject().contains("\n"));
    }

    @Test
    public void contentTypeAndFileNameAreSanitized() {
        assertEquals("application/octet-stream", MailRequestValidator.safeContentType("text/html\r\nX: y"));
        assertEquals("text/csv", MailRequestValidator.safeContentType("text/csv"));
        assertEquals("attachment", MailRequestValidator.safeFileName(null));
        assertEquals("attachment", MailRequestValidator.safeFileName("../"));
        assertEquals("a_b.csv", MailRequestValidator.safeFileName("a:b.csv"));
    }

    @Test
    public void requestIsJsonRoundTrip() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        JsonMailRequest request = request("a@example.com");
        request.getUserIds().add(5L);
        request.getGroupIds().add(9L);
        request.getAttachments().add(new JsonMailRequest.Attachment("a.csv", "text/csv", base64(3)));
        JsonMailRequest read = mapper.readValue(mapper.writeValueAsString(request), JsonMailRequest.class);
        assertEquals(Collections.singletonList(5L), read.getUserIds());
        assertEquals(Collections.singletonList(9L), read.getGroupIds());
        assertEquals("a.csv", read.getAttachments().get(0).getFileName());
    }
}
