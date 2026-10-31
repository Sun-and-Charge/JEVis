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
package org.jevis.commons.appointment;

import org.jevis.api.*;
import org.jevis.commons.mail.MailMessage;
import org.jevis.commons.maintenance.MaintenanceEntry;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ScheduledMailProcessorTest {

    private JEVisDataSource ds;
    private final List<MailMessage> sent = new ArrayList<>();
    private final List<String> stateWrites = new ArrayList<>();
    private String stateValue;

    @Before
    public void setUp() throws Exception {
        ds = mock(JEVisDataSource.class);
        JEVisObject user = mock(JEVisObject.class);
        when(user.getID()).thenReturn(12L);
        when(user.getName()).thenReturn("user12");
        when(user.getJEVisClassName()).thenReturn("User");
        JEVisAttribute mail = attribute("user@example.org");
        when(mail.hasSample()).thenReturn(true);
        when(user.getAttribute("E-Mail")).thenReturn(mail);
        when(ds.getObject(12L)).thenReturn(user);
    }

    private JEVisAttribute attribute(String value) throws Exception {
        JEVisAttribute attribute = mock(JEVisAttribute.class);
        if (value != null) {
            JEVisSample sample = mock(JEVisSample.class);
            when(sample.getValueAsString()).thenReturn(value);
            when(attribute.getLatestSample()).thenReturn(sample);
            when(attribute.hasSample()).thenReturn(true);
        }
        return attribute;
    }

    private JEVisAttribute stateAttribute() throws Exception {
        JEVisAttribute attribute = mock(JEVisAttribute.class);
        when(attribute.getLatestSample()).thenAnswer(invocation -> {
            if (stateValue == null) return null;
            JEVisSample sample = mock(JEVisSample.class);
            when(sample.getValueAsString()).thenReturn(stateValue);
            return sample;
        });
        when(attribute.buildSample(any(DateTime.class), any())).thenAnswer(invocation -> {
            String value = (String) invocation.getArgument(1);
            JEVisSample sample = mock(JEVisSample.class);
            org.mockito.Mockito.doAnswer(commit -> {
                stateValue = value;
                stateWrites.add(value);
                return null;
            }).when(sample).commit();
            return sample;
        });
        return attribute;
    }

    private void register(String className, Object entry) throws Exception {
        JEVisClass jeVisClass = mock(JEVisClass.class);
        when(ds.getJEVisClass(className)).thenReturn(jeVisClass);
        JEVisObject object = mock(JEVisObject.class);
        when(object.getID()).thenReturn(100L);
        when(object.getName()).thenReturn("entry");
        JEVisAttribute data = attribute(EntryFormat.toJson(entry));
        JEVisAttribute state = stateAttribute();
        when(object.getAttribute(EntryFormat.ATTRIBUTE_DATA)).thenReturn(data);
        when(object.getAttribute(EntryFormat.ATTRIBUTE_MAIL_STATE)).thenReturn(state);
        when(ds.getObjects(jeVisClass, false)).thenReturn(Collections.singletonList(object));
    }

    @Test
    public void appointmentReminderIsSentOnce() throws Exception {
        Appointment appointment = new Appointment();
        appointment.setTitle("Verification");
        appointment.setDue("2026-11-30T09:00");
        appointment.setReminderDays(7);
        appointment.getRecipients().add(12L);
        register(Appointment.CLASS_NAME, appointment);

        ScheduledMailProcessor processor = new ScheduledMailProcessor(ds, sent::add);
        ScheduledMailProcessor.Result result = processor.run(LocalDateTime.parse("2026-11-24T08:00"));
        assertEquals(1, result.getSent());
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).getTo().contains("user@example.org"));
        assertTrue(sent.get(0).getSubject().startsWith("Erinnerung"));
        MailState state = EntryFormat.fromJson(stateValue, MailState.class);
        assertEquals("2026-11-30T09:00", state.getReminderSentFor());

        processor.run(LocalDateTime.parse("2026-11-24T08:15"));
        assertEquals("no repeated mail", 1, sent.size());

        processor.run(LocalDateTime.parse("2026-11-30T00:15"));
        assertEquals(2, sent.size());
        assertTrue(sent.get(1).getSubject().startsWith("Heute fällig"));
    }

    @Test
    public void failedSendIsRecordedAndRetried() throws Exception {
        MaintenanceEntry entry = new MaintenanceEntry();
        entry.setTitle("Update");
        entry.setInformRequested("2026-11-01T10:00:00");
        entry.getRecipients().add(12L);
        register(MaintenanceEntry.CLASS_NAME, entry);

        ScheduledMailProcessor failing = new ScheduledMailProcessor(ds, message -> {
            throw new Exception("SMTP down");
        });
        ScheduledMailProcessor.Result result = failing.run(LocalDateTime.parse("2026-11-01T10:05"));
        assertEquals(1, result.getFailed());
        assertEquals("SMTP down", EntryFormat.fromJson(stateValue, MailState.class).getLastError());
        failing.run(LocalDateTime.parse("2026-11-01T10:20"));
        assertEquals("same error is not written again", 1, stateWrites.size());

        new ScheduledMailProcessor(ds, sent::add).run(LocalDateTime.parse("2026-11-01T10:35"));
        assertEquals(1, sent.size());
        assertTrue("maintenance messages go out as blind copy", sent.get(0).getTo().isEmpty());
        assertEquals(1, sent.get(0).getBcc().size());
        MailState state = EntryFormat.fromJson(stateValue, MailState.class);
        assertNull(state.getLastError());
        assertEquals("2026-11-01T10:00:00", state.getInformSentFor());
    }

    @Test
    public void noRecipientsNoMail() throws Exception {
        MaintenanceEntry entry = new MaintenanceEntry();
        entry.setTitle("Update");
        entry.setInformRequested("2026-11-01T10:00:00");
        register(MaintenanceEntry.CLASS_NAME, entry);
        new ScheduledMailProcessor(ds, sent::add).run(LocalDateTime.parse("2026-11-01T10:05"));
        assertTrue(sent.isEmpty());
        assertNotNull(EntryFormat.fromJson(stateValue, MailState.class).getLastError());
    }
}
