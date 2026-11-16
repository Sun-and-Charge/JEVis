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
package org.jevis.jenotifier;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.appointment.MailState;
import org.jevis.commons.appointment.ScheduledMailProcessor;
import org.jevis.commons.classes.JC;
import org.jevis.commons.mail.MailMessage;
import org.jevis.commons.mail.MailRecipients;
import org.jevis.commons.reading.RoundMailInfo;
import org.jevis.commons.reading.RoundMailLogic;
import org.jevis.commons.reading.RoundMails;
import org.jevis.jeapi.ws.JEVisDataSourceWS;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One cycle of the reading round mails (stage E8.3, docs/WEB-API.md): order, reminder and escalation per due date,
 * run by the JENotifier service after the appointment and maintenance mails.
 * <p>
 * The rounds come from {@code GET /web/reading-rounds} of the web service (due date, state and done meters are
 * computed there), the route sheet from {@code GET /web/reading-rounds/{id}/sheet.pdf}. Which mail is due decides
 * {@link RoundMailLogic}; the round's "Mail State" records it, so each mail goes out once per due date. A round without
 * recipients or with a failed mail gets the error recorded and is tried again in the next cycle; a failing round never
 * stops the others.
 */
public class ReadingRoundMailProcessor {

    private static final Logger logger = LogManager.getLogger(ReadingRoundMailProcessor.class);
    static final String NO_RECIPIENTS = "No recipients with an e-mail address";

    /**
     * Where the rounds, their route sheets, mail states and recipient addresses come from.
     */
    public interface RoundSource {
        /**
         * @return the answer of {@code GET /web/reading-rounds}: {@code {today, rounds: [...]}}
         */
        JsonNode rounds() throws Exception;

        /**
         * @return the route sheet PDF of the round for the due date
         */
        byte[] routeSheet(long roundId, LocalDate due) throws Exception;

        /**
         * @return the latest value of the round's "Mail State", null if empty
         * @throws Exception if the round has no such attribute (no mail may be sent then)
         */
        String mailState(long roundId) throws Exception;

        void writeMailState(long roundId, String json) throws Exception;

        /**
         * @return the e-mail addresses of the users and groups ({@link MailRecipients#resolve})
         */
        Set<String> addresses(List<Long> usersOrGroups);
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

    private final RoundSource source;
    private final ScheduledMailProcessor.Sender sender;
    private final String webAppUrl;

    /**
     * @param webAppUrl the "Web App URL" of the JENotifier service for the links in the mails, or null
     */
    public ReadingRoundMailProcessor(RoundSource source, ScheduledMailProcessor.Sender sender, String webAppUrl) {
        this.source = source;
        this.sender = sender;
        this.webAppUrl = webAppUrl;
    }

    public Result run(LocalDateTime now) {
        Result result = new Result();
        JsonNode list;
        try {
            list = source.rounds();
        } catch (Exception e) {
            logger.error("Could not load the reading rounds", e);
            result.failed++;
            return result;
        }
        if (list == null || !list.path("rounds").isArray()) {
            logger.warn("No reading rounds in the answer of the web service");
            return result;
        }
        LocalDate today = date(list.path("today").asText(null));
        if (today == null) today = now.toLocalDate();
        for (JsonNode node : list.get("rounds")) {
            RoundMailInfo round = null;
            try {
                round = parse(node);
                process(round, today, now, result);
            } catch (Exception e) {
                result.failed++;
                logger.error("Could not process reading round {}", round != null ? round.id : node.path("id").asText(), e);
            }
        }
        return result;
    }

    void process(RoundMailInfo round, LocalDate today, LocalDateTime now, Result result) throws Exception {
        if (RoundMailLogic.latestApplicable(round, today) == null) {
            return;
        }
        MailState state = EntryFormat.fromJson(source.mailState(round.id), MailState.class);
        if (state == null) state = new MailState();
        RoundMailLogic.MailKind kind = RoundMailLogic.pendingMail(round, state, today);
        if (kind == null) {
            return;
        }
        boolean escalation = kind == RoundMailLogic.MailKind.ESCALATION;
        List<Long> toIds = ids(escalation ? round.supervisor : round.readers);
        if (escalation && toIds.isEmpty()) {
            RoundMailLogic.markHandled(kind, round.due, state);
            writeState(round, state);
            logger.info("Reading round {} ({}) has no supervisor, escalation for {} not sent", round.name, round.id, round.due);
            return;
        }
        Set<String> to = source.addresses(toIds);
        if (to.isEmpty()) {
            logger.warn("No recipients with an e-mail address for reading round {} ({})", round.name, round.id);
            recordError(round, state, NO_RECIPIENTS);
            result.failed++;
            return;
        }

        byte[] pdf = null;
        try {
            pdf = source.routeSheet(round.id, round.due);
            if (pdf != null && pdf.length == 0) pdf = null;
        } catch (Exception e) {
            logger.warn("Could not load the route sheet of reading round {} ({}), mail sent without it", round.name, round.id, e);
        }
        if (pdf == null) {
            logger.warn("Reading round {} ({}): mail {} without route sheet", round.name, round.id, kind);
        }

        MailMessage mail = RoundMails.build(kind, round, webAppUrl, pdf != null);
        mail.to(to);
        if (escalation) {
            Set<String> cc = new LinkedHashSet<>(source.addresses(ids(round.readers)));
            cc.removeAll(to);
            mail.cc(cc);
        }
        if (pdf != null) {
            mail.attach(RoundMails.attachmentName(round), pdf, RoundMails.ATTACHMENT_TYPE);
        }
        try {
            sender.send(mail);
        } catch (Exception e) {
            logger.error("Could not send mail {} for reading round {} ({})", kind, round.name, round.id, e);
            recordError(round, state, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            result.failed++;
            return;
        }
        result.sent++;
        RoundMailLogic.markSent(kind, round.due, state, now);
        writeState(round, state);
        logger.info("Reading round mail {} sent for {} ({}), due {}", kind, round.name, round.id, round.due);
    }

    private void recordError(RoundMailInfo round, MailState state, String error) {
        if (Objects.equals(state.getLastError(), error)) {
            return;
        }
        state.setLastError(error);
        try {
            writeState(round, state);
        } catch (Exception e) {
            logger.error("Could not save the mail state of reading round {}", round.id, e);
        }
    }

    private void writeState(RoundMailInfo round, MailState state) throws Exception {
        source.writeMailState(round.id, EntryFormat.toJson(state));
    }

    private static List<Long> ids(List<RoundMailInfo.Person> people) {
        List<Long> ids = new ArrayList<>();
        for (RoundMailInfo.Person person : people) {
            if (!ids.contains(person.id)) ids.add(person.id);
        }
        return ids;
    }

    // ---------------------------------------------------------------- the list of the web service

    static RoundMailInfo parse(JsonNode node) {
        RoundMailInfo round = new RoundMailInfo();
        round.id = node.path("id").asLong();
        round.name = text(node, "name");
        round.siteName = text(node.path("site"), "name");
        round.enabled = node.path("enabled").asBoolean(false);
        round.period = text(node, "period");
        round.due = date(text(node, "due"));
        round.state = text(node, "state");
        round.reminderDays = node.path("reminderDays").asInt(3);
        round.escalationDays = node.path("escalationDays").asInt(7);
        round.remarks = text(node, "remarks");
        round.done = node.path("done").asInt();
        round.total = node.path("total").asInt();
        round.readers = people(node.path("readers"));
        round.supervisor = people(node.path("supervisor"));
        for (JsonNode element : node.path("meters")) {
            RoundMailInfo.Meter meter = new RoundMailInfo.Meter();
            meter.id = element.path("id").asLong();
            meter.readable = element.path("readable").asBoolean(false);
            meter.name = text(element, "name");
            meter.location = text(element, "location");
            meter.done = element.path("done").asBoolean(false);
            meter.automatic = element.path("automatic").asBoolean(false);
            round.meters.add(meter);
        }
        return round;
    }

    private static List<RoundMailInfo.Person> people(JsonNode array) {
        List<RoundMailInfo.Person> people = new ArrayList<>();
        for (JsonNode element : array) {
            if (!element.path("id").canConvertToLong()) continue;
            RoundMailInfo.Person person = new RoundMailInfo.Person();
            person.id = element.path("id").asLong();
            person.name = text(element, "name");
            person.kind = text(element, "kind");
            people.add(person);
        }
        return people;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        return value != null && !value.isNull() ? value.asText() : null;
    }

    static LocalDate date(String text) {
        if (text == null || !text.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        try {
            return LocalDate.parse(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- the service's data source

    /**
     * The rounds and route sheets from the web service, mail states and recipients through the data source of the
     * service.
     */
    public static class WebRoundSource implements RoundSource {
        private final JEVisDataSourceWS ds;
        private final String webAppUrl;

        public WebRoundSource(JEVisDataSourceWS ds) {
            this(ds, null);
        }

        /**
         * @param webAppUrl the "Web App URL", passed on so the QR codes of the route sheet use the public address and
         *                  not the one the service reaches the web service on; may be null
         */
        public WebRoundSource(JEVisDataSourceWS ds, String webAppUrl) {
            this.ds = ds;
            this.webAppUrl = webAppUrl;
        }

        @Override
        public JsonNode rounds() throws Exception {
            return ds.getJson("web/reading-rounds?lang=de");
        }

        @Override
        public byte[] routeSheet(long roundId, LocalDate due) throws Exception {
            String app = webAppUrl != null && !webAppUrl.trim().isEmpty()
                    ? "&appUrl=" + java.net.URLEncoder.encode(webAppUrl.trim(), "UTF-8") : "";
            return ds.getBytes("web/reading-rounds/" + roundId + "/sheet.pdf?lang=de&due=" + due + app);
        }

        @Override
        public String mailState(long roundId) throws Exception {
            JEVisAttribute attribute = mailStateAttribute(roundId);
            try {
                ds.reloadAttribute(attribute);
            } catch (Exception e) {
                logger.debug("Could not reload the mail state of {}", roundId, e);
            }
            JEVisSample sample = attribute.getLatestSample();
            return sample != null ? sample.getValueAsString() : null;
        }

        @Override
        public void writeMailState(long roundId, String json) throws Exception {
            JEVisSample sample = mailStateAttribute(roundId).buildSample(new DateTime(), json);
            sample.commit();
        }

        private JEVisAttribute mailStateAttribute(long roundId) throws Exception {
            JEVisObject object = ds.getObject(roundId);
            if (object == null) {
                throw new IllegalStateException("Reading round " + roundId + " not found or not readable");
            }
            JEVisAttribute attribute = object.getAttribute(JC.ReadingRound.a_MailState);
            if (attribute == null) {
                throw new IllegalStateException("Reading round " + roundId + " has no attribute '"
                        + JC.ReadingRound.a_MailState + "', no mail sent");
            }
            return attribute;
        }

        @Override
        public Set<String> addresses(List<Long> usersOrGroups) {
            return resolve(ds, usersOrGroups);
        }
    }

    static Set<String> resolve(JEVisDataSource ds, List<Long> ids) {
        List<JEVisObject> objects = new ArrayList<>();
        for (Long id : ids) {
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
        return objects.isEmpty() ? Collections.<String>emptySet() : MailRecipients.resolve(ds, objects);
    }
}
