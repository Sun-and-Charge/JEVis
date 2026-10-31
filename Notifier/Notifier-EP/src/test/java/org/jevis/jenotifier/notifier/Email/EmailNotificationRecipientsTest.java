/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of Notifier.
 * <p>
 * Notifier is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * Notifier is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * Notifier. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * Notifier is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jenotifier.notifier.Email;

import org.jevis.api.*;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Typed recipients are merged with the users and groups of "Recipient Users" / "Recipient Groups" (upstream #1437).
 */
public class EmailNotificationRecipientsTest {

    private JEVisDataSource ds;
    private JEVisObject notification;
    private final List<JEVisRelationship> memberships = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        ds = mock(JEVisDataSource.class);
        when(ds.getRelationships(anyInt())).thenReturn(Collections.emptyList());
        when(ds.getRelationships(JEVisConstants.ObjectRelationship.MEMBER_READ)).thenReturn(memberships);

        JEVisClass notificationClass = mock(JEVisClass.class);
        when(notificationClass.getName()).thenReturn(EmailNotification._type);
        notification = mock(JEVisObject.class);
        when(notification.getJEVisClass()).thenReturn(notificationClass);
        when(notification.getDataSource()).thenReturn(ds);
    }

    private static JEVisAttribute stringAttribute(String value) throws Exception {
        JEVisSample sample = mock(JEVisSample.class);
        when(sample.getValue()).thenReturn(value);
        when(sample.getValueAsString()).thenReturn(value);
        JEVisAttribute attribute = mock(JEVisAttribute.class);
        when(attribute.hasSample()).thenReturn(true);
        when(attribute.getLatestSample()).thenReturn(sample);
        return attribute;
    }

    private void setAttribute(String name, String value) throws Exception {
        JEVisAttribute attribute = stringAttribute(value);
        when(notification.getAttribute(name)).thenReturn(attribute);
    }

    private JEVisObject user(long id, String email) throws Exception {
        JEVisObject user = mock(JEVisObject.class);
        when(user.getID()).thenReturn(id);
        when(user.getName()).thenReturn("User " + id);
        when(user.getJEVisClassName()).thenReturn("User");
        if (email != null) {
            JEVisAttribute mail = stringAttribute(email);
            when(user.getAttribute("E-Mail")).thenReturn(mail);
        }
        when(ds.getObject(id)).thenReturn(user);
        return user;
    }

    private JEVisObject group(long id, JEVisObject... members) throws Exception {
        JEVisObject group = mock(JEVisObject.class);
        when(group.getID()).thenReturn(id);
        when(group.getJEVisClassName()).thenReturn("Group");
        when(ds.getObject(id)).thenReturn(group);
        for (JEVisObject member : members) {
            JEVisRelationship relationship = mock(JEVisRelationship.class);
            long memberId = member.getID();
            when(relationship.getStartID()).thenReturn(memberId);
            when(relationship.getEndID()).thenReturn(id);
            when(relationship.getStartObject()).thenReturn(member);
            memberships.add(relationship);
        }
        return group;
    }

    @Test
    public void typedRecipientsAreMergedWithUsersAndGroups() throws Exception {
        setAttribute(EmailNotification.RECIPIENTS, "first@example.com; second@example.com");
        user(10L, "second@example.com");
        JEVisObject member1 = user(11L, "member1@example.com");
        JEVisObject member2 = user(12L, "member2@example.com");
        group(20L, member1, member2);
        setAttribute(EmailNotification.RECIPIENT_USERS, "10:Value;11");
        setAttribute(EmailNotification.RECIPIENT_GROUPS, "20:Value");

        EmailNotification emailNotification = new EmailNotification();
        emailNotification.setNotificationObject(notification);

        assertEquals(Arrays.asList("first@example.com", "second@example.com", "member1@example.com", "member2@example.com"),
                emailNotification.getReceivers());
        assertTrue(emailNotification.isNotiConfigured());
    }

    @Test
    public void usersAloneConfigureTheNotification() throws Exception {
        user(10L, "only@example.com");
        setAttribute(EmailNotification.RECIPIENT_USERS, "10");

        JEVisFile attachment = mock(JEVisFile.class);
        when(attachment.getFilename()).thenReturn("EmailNotificationRecipientsTest.txt");
        when(attachment.getBytes()).thenReturn(new byte[]{1});

        EmailNotification emailNotification = new EmailNotification();
        emailNotification.setNotificationObject(notification, attachment);

        assertEquals(Collections.singletonList("only@example.com"), emailNotification.getReceivers());
        assertTrue(emailNotification.isNotiConfigured());
    }

    @Test
    public void typedRecipientsStayWithoutUsersOrGroups() throws Exception {
        setAttribute(EmailNotification.RECIPIENTS, "first@example.com");

        EmailNotification emailNotification = new EmailNotification();
        emailNotification.setNotificationObject(notification);

        assertEquals(Collections.singletonList("first@example.com"), emailNotification.getReceivers());
    }

    @Test
    public void illegalAndMissingAddressesAreSkipped() throws Exception {
        setAttribute(EmailNotification.RECIPIENTS, "first@example.com");
        user(10L, "not-an-address");
        user(11L, null);
        setAttribute(EmailNotification.RECIPIENT_USERS, "10;11;99");

        EmailNotification emailNotification = new EmailNotification();
        emailNotification.setNotificationObject(notification);

        assertEquals(Collections.singletonList("first@example.com"), emailNotification.getReceivers());
    }
}
