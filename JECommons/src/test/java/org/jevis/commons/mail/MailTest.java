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

import org.jevis.api.*;
import org.junit.Test;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MailTest {

    private static JEVisObject user(long id, String email, boolean enabled) throws Exception {
        JEVisObject user = mock(JEVisObject.class);
        when(user.getID()).thenReturn(id);
        when(user.getName()).thenReturn("user" + id);
        when(user.getJEVisClassName()).thenReturn("User");
        JEVisAttribute mail = mock(JEVisAttribute.class);
        JEVisSample mailSample = mock(JEVisSample.class);
        when(mailSample.getValueAsString()).thenReturn(email);
        when(mail.hasSample()).thenReturn(email != null);
        when(mail.getLatestSample()).thenReturn(mailSample);
        when(user.getAttribute("E-Mail")).thenReturn(mail);
        JEVisAttribute enabledAtt = mock(JEVisAttribute.class);
        JEVisSample enabledSample = mock(JEVisSample.class);
        when(enabledSample.getValueAsBoolean()).thenReturn(enabled);
        when(enabledAtt.hasSample()).thenReturn(true);
        when(enabledAtt.getLatestSample()).thenReturn(enabledSample);
        when(user.getAttribute("Enabled")).thenReturn(enabledAtt);
        return user;
    }

    private static JEVisRelationship membership(JEVisObject user, long groupId) throws Exception {
        JEVisRelationship rel = mock(JEVisRelationship.class);
        long userId = user.getID();
        when(rel.getStartID()).thenReturn(userId);
        when(rel.getEndID()).thenReturn(groupId);
        when(rel.getStartObject()).thenReturn(user);
        return rel;
    }

    @Test
    public void parseAddressesAndIds() {
        assertEquals(Arrays.asList("a@example.org", "b@example.org"), MailRecipients.parseAddresses(" a@example.org; b@example.org,a@example.org "));
        assertTrue(MailRecipients.parseAddresses(null).isEmpty());
        assertEquals(Arrays.asList(12L, 15L), MailRecipients.parseIds("12;15:Value; x;12"));
    }

    @Test
    public void usersAndGroupsAreResolved() throws Exception {
        JEVisDataSource ds = mock(JEVisDataSource.class);
        JEVisObject anna = user(1, "anna@example.org", true);
        JEVisObject ben = user(2, "ben@example.org; ben.private@example.org", true);
        JEVisObject disabled = user(3, "old@example.org", false);
        JEVisObject group = mock(JEVisObject.class);
        when(group.getID()).thenReturn(50L);
        when(group.getJEVisClassName()).thenReturn("Group");
        java.util.List<JEVisRelationship> read = Arrays.asList(membership(ben, 50), membership(disabled, 50), membership(anna, 51));
        java.util.List<JEVisRelationship> write = Collections.singletonList(membership(ben, 50));
        when(ds.getRelationships(JEVisConstants.ObjectRelationship.MEMBER_READ)).thenReturn(read);
        when(ds.getRelationships(JEVisConstants.ObjectRelationship.MEMBER_WRITE)).thenReturn(write);

        Set<String> addresses = MailRecipients.resolve(ds, Arrays.asList(anna, group));

        assertEquals(Arrays.asList("anna@example.org", "ben@example.org", "ben.private@example.org"), Arrays.asList(addresses.toArray()));
    }

    @Test
    public void settingsValues() {
        assertEquals(MailSettings.Security.NONE, MailSettings.parseSecurity("<<No>>"));
        assertEquals(MailSettings.Security.SSL, MailSettings.parseSecurity("ssl"));
        assertEquals(MailSettings.Security.STARTTLS, MailSettings.parseSecurity(null));
        assertEquals(587, MailSettings.parsePort("587"));
        assertEquals(25, MailSettings.parsePort("x"));

        MailSettings settings = new MailSettings("smtp.example.org", 465, "jevis@example.org", "secret", MailSettings.Security.SSL, true);
        Properties properties = MailSender.properties(settings);
        assertEquals("true", properties.getProperty("mail.smtp.ssl.enable"));
        assertEquals("465", properties.getProperty("mail.smtp.port"));
        assertNull(properties.getProperty("mail.smtp.starttls.enable"));
    }

    @Test
    public void messageWithAttachment() throws Exception {
        MailSettings settings = new MailSettings("smtp.example.org", 587, "jevis@example.org", "secret", MailSettings.Security.STARTTLS, true);
        MailMessage mail = new MailMessage().to("anna@example.org; ben@example.org").subject("Analyse Übersicht")
                .text("Hallo", false).attach("Analyse März.pdf", "pdf".getBytes(StandardCharsets.UTF_8), "application/pdf");

        MimeMessage message = new MailSender(settings).build(mail, Session.getInstance(MailSender.properties(settings)));

        assertEquals("jevis@example.org", message.getFrom()[0].toString());
        assertEquals(2, message.getRecipients(Message.RecipientType.TO).length);
        assertEquals("Analyse Übersicht", message.getSubject());
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        assertEquals(2, multipart.getCount());
        assertEquals("Analyse März.pdf", javax.mail.internet.MimeUtility.decodeText(multipart.getBodyPart(1).getFileName()));
    }

    @Test(expected = javax.mail.MessagingException.class)
    public void missingServerIsRejected() throws Exception {
        new MailSender(new MailSettings()).send(new MailMessage().to("anna@example.org"));
    }
}
