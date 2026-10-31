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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.mail.MailMessage;
import org.jevis.commons.mail.MailRecipients;
import org.jevis.commons.maintenance.MaintenanceEntry;
import org.jevis.commons.maintenance.MaintenanceLogic;
import org.jevis.commons.maintenance.MaintenanceMails;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One cycle of the mail job for appointments (#1855) and maintenance messages (#2663), run by the JENotifier service.
 * <p>
 * For each entry at most one mail is sent per cycle. The mail state is saved after each sent mail; a mail is only sent
 * if the entry has a "Mail State" attribute to record it, so mails are not repeated. Errors (no recipients, SMTP
 * failure) are recorded in the mail state and the mail is tried again in the next cycle.
 */
public class ScheduledMailProcessor {

    private static final Logger logger = LogManager.getLogger(ScheduledMailProcessor.class);

    /**
     * Sends a mail, e.g. {@code new MailSender(settings)::send}.
     */
    public interface Sender {
        void send(MailMessage message) throws Exception;
    }

    public static class Result {
        private int sent;
        private int failed;

        public int getSent() {
            return sent;
        }

        public int getFailed() {
            return failed;
        }

        @Override
        public String toString() {
            return sent + " mail(s) sent, " + failed + " failed";
        }
    }

    private final JEVisDataSource ds;
    private final Sender sender;

    public ScheduledMailProcessor(JEVisDataSource ds, Sender sender) {
        this.ds = ds;
        this.sender = sender;
    }

    public Result run(LocalDateTime now) {
        Result result = new Result();
        for (JEVisObject object : objectsOf(Appointment.CLASS_NAME)) {
            processAppointment(object, now, result);
        }
        for (JEVisObject object : objectsOf(MaintenanceEntry.CLASS_NAME)) {
            processMaintenance(object, now, result);
        }
        return result;
    }

    private List<JEVisObject> objectsOf(String className) {
        try {
            JEVisClass jeVisClass = ds.getJEVisClass(className);
            if (jeVisClass == null) {
                logger.debug("Class {} does not exist", className);
                return Collections.emptyList();
            }
            List<JEVisObject> objects = ds.getObjects(jeVisClass, false);
            return objects != null ? objects : Collections.<JEVisObject>emptyList();
        } catch (Exception e) {
            logger.error("Could not load objects of class {}", className, e);
            return Collections.emptyList();
        }
    }

    void processAppointment(JEVisObject object, LocalDateTime now, Result result) {
        try {
            JEVisAttribute dataAttribute = object.getAttribute(EntryFormat.ATTRIBUTE_DATA);
            JEVisAttribute stateAttribute = object.getAttribute(EntryFormat.ATTRIBUTE_MAIL_STATE);
            Appointment appointment = EntryFormat.fromJson(latest(dataAttribute), Appointment.class);
            if (appointment == null) {
                return;
            }
            MailState state = readState(stateAttribute);
            AppointmentLogic.MailKind kind = AppointmentLogic.pendingMail(appointment, state, now);
            if (kind == null) {
                return;
            }
            if (stateAttribute == null) {
                logger.error("Appointment {} has no attribute '{}', no mail sent", object.getID(), EntryFormat.ATTRIBUTE_MAIL_STATE);
                return;
            }
            MailMessage mail = AppointmentMails.build(kind, appointment, objectName(appointment.getLinkedObject()), now.toLocalDate());
            if (send(mail, appointment.getRecipients(), false, state, stateAttribute, object, result)) {
                AppointmentLogic.markSent(kind, appointment, state, now);
                writeState(stateAttribute, state);
                logger.info("Appointment mail {} sent for {} ({})", kind, object.getName(), object.getID());
            }
        } catch (Exception e) {
            result.failed++;
            logger.error("Could not process appointment {}", object, e);
        }
    }

    void processMaintenance(JEVisObject object, LocalDateTime now, Result result) {
        try {
            JEVisAttribute dataAttribute = object.getAttribute(EntryFormat.ATTRIBUTE_DATA);
            JEVisAttribute stateAttribute = object.getAttribute(EntryFormat.ATTRIBUTE_MAIL_STATE);
            MaintenanceEntry entry = EntryFormat.fromJson(latest(dataAttribute), MaintenanceEntry.class);
            if (entry == null) {
                return;
            }
            MailState state = readState(stateAttribute);
            MaintenanceLogic.MailKind kind = MaintenanceLogic.pendingMail(entry, state);
            if (kind == null) {
                return;
            }
            if (stateAttribute == null) {
                logger.error("Maintenance message {} has no attribute '{}', no mail sent", object.getID(), EntryFormat.ATTRIBUTE_MAIL_STATE);
                return;
            }
            MailMessage mail = MaintenanceMails.build(kind, entry);
            if (send(mail, entry.getRecipients(), true, state, stateAttribute, object, result)) {
                MaintenanceLogic.markSent(kind, entry, state, now);
                writeState(stateAttribute, state);
                logger.info("Maintenance mail {} sent for {} ({})", kind, object.getName(), object.getID());
            }
        } catch (Exception e) {
            result.failed++;
            logger.error("Could not process maintenance message {}", object, e);
        }
    }

    /**
     * @param blind send as blind copy (maintenance messages to whole groups), so recipients do not see each other's
     *              addresses; appointment reminders go to the responsible persons openly
     */
    private boolean send(MailMessage mail, List<Long> recipientIds, boolean blind, MailState state, JEVisAttribute stateAttribute,
                         JEVisObject object, Result result) throws Exception {
        Set<String> addresses = MailRecipients.resolve(ds, recipientObjects(recipientIds));
        if (addresses.isEmpty()) {
            logger.warn("No recipients with an e-mail address for {} ({})", object.getName(), object.getID());
            recordError(stateAttribute, state, "No recipients with an e-mail address");
            result.failed++;
            return false;
        }
        if (blind) {
            mail.bcc(addresses);
        } else {
            mail.to(addresses);
        }
        try {
            sender.send(mail);
        } catch (Exception e) {
            logger.error("Could not send mail for {} ({})", object.getName(), object.getID(), e);
            recordError(stateAttribute, state, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            result.failed++;
            return false;
        }
        result.sent++;
        return true;
    }

    private List<JEVisObject> recipientObjects(List<Long> ids) {
        List<JEVisObject> objects = new ArrayList<>();
        for (Long id : ids) {
            if (id == null) continue;
            try {
                JEVisObject object = ds.getObject(id);
                if (object != null) {
                    objects.add(object);
                } else {
                    logger.warn("Mail recipient {} not found or not readable", id);
                }
            } catch (Exception e) {
                logger.warn("Could not load mail recipient {}", id, e);
            }
        }
        return objects;
    }

    private String objectName(Long id) {
        if (id == null) {
            return null;
        }
        try {
            JEVisObject object = ds.getObject(id);
            return object != null ? object.getName() : null;
        } catch (Exception e) {
            logger.warn("Could not load linked object {}", id, e);
            return null;
        }
    }

    private void recordError(JEVisAttribute stateAttribute, MailState state, String error) {
        if (Objects.equals(state.getLastError(), error)) {
            return;
        }
        state.setLastError(error);
        try {
            writeState(stateAttribute, state);
        } catch (Exception e) {
            logger.error("Could not save mail state", e);
        }
    }

    private MailState readState(JEVisAttribute stateAttribute) {
        MailState state = EntryFormat.fromJson(latest(stateAttribute), MailState.class);
        return state != null ? state : new MailState();
    }

    private void writeState(JEVisAttribute stateAttribute, MailState state) throws Exception {
        JEVisSample sample = stateAttribute.buildSample(new DateTime(), EntryFormat.toJson(state));
        sample.commit();
    }

    private String latest(JEVisAttribute attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            ds.reloadAttribute(attribute);
        } catch (Exception e) {
            logger.debug("Could not reload attribute {}", attribute, e);
        }
        try {
            JEVisSample sample = attribute.getLatestSample();
            return sample != null ? sample.getValueAsString() : null;
        } catch (Exception e) {
            logger.warn("Could not read attribute {}", attribute, e);
            return null;
        }
    }
}
