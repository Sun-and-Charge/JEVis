/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.certificate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.mail.MailMessage;
import org.jevis.commons.mail.MailRecipients;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import java.util.*;

/**
 * One cycle of the energy certificate job (BA-07, docs/ENERGY-CERTIFICATES.md), run by the JENotifier service:
 * <ol>
 *     <li>reminders before expiry to the responsible users and groups (once per reminder month and valid-until
 *     date, recorded in "Transfer State");</li>
 *     <li>transfers: each enabled "Energy Certificate Transfer" that is requested or due sends the certificates below
 *     its parent by mail, SFTP or HTTP and records the result in "Last Transfer" and in each certificate.</li>
 * </ol>
 * A failing certificate or target never stops the others.
 */
public class CertificateProcessor {

    public static final String CLASS_TRANSFER = "Energy Certificate Transfer";
    static final String CLASS_BUILDING = "Building";
    static final String CLASS_ORGANIZATION = "Organization";

    private static final Logger logger = LogManager.getLogger(CertificateProcessor.class);

    /** The ways out; production: mail sender of the JENotifier, its SFTP client and {@link CertificateHttp}. */
    public interface Channels {
        void mail(MailMessage message) throws Exception;

        /**
         * Uploads the file into the directory.
         *
         * @param knownHostKey fingerprint accepted before (null: accept the key on first use)
         * @return the fingerprint of the server key
         */
        String sftp(String host, int port, String user, String password, String directory, String knownHostKey,
                    String fileName, byte[] content) throws Exception;

        void http(String url, String token, String fileName, byte[] content, String contentType) throws Exception;
    }

    public static class Result {
        int reminders;
        int transfers;
        int failed;

        @Override
        public String toString() {
            return reminders + " reminder(s), " + transfers + " transfer(s), " + failed + " failed";
        }
    }

    /** One certificate as read in this cycle. */
    static class Item {
        JEVisObject object;
        EnergyCertificate certificate;
        String building;
        String version;
        JEVisSample file;
        List<Long> ancestors = new ArrayList<>();
    }

    private final JEVisDataSource ds;
    private final Channels channels;
    private final String webAppUrl;

    public CertificateProcessor(JEVisDataSource ds, Channels channels, String webAppUrl) {
        this.ds = ds;
        this.channels = channels;
        this.webAppUrl = webAppUrl;
    }

    public Result run(DateTime now) {
        Result result = new Result();
        List<Item> items = new ArrayList<>();
        for (JEVisObject object : objectsOf(EnergyCertificate.CLASS_NAME)) {
            try {
                Item item = item(object);
                if (item != null) items.add(item);
            } catch (Exception e) {
                result.failed++;
                logger.error("Could not read the energy certificate {}", object, e);
            }
        }
        for (Item item : items) remind(item, now, result);
        for (JEVisObject target : objectsOf(CLASS_TRANSFER)) transfer(target, items, now, result);
        return result;
    }

    // ---------------------------------------------------------------- reminders

    void remind(Item item, DateTime now, Result result) {
        try {
            JEVisAttribute stateAttribute = item.object.getAttribute(EnergyCertificate.ATTRIBUTE_TRANSFER_STATE);
            if (stateAttribute == null) return;
            CertificateState state = CertificateState.fromJson(latest(stateAttribute));
            if (item.certificate.getResponsible().isEmpty()) {
                /* nobody responsible: no reminders wanted, not an error (an old "no recipients" error is removed) */
                if (state.getLastError() != null) {
                    state.setLastError(null);
                    write(stateAttribute, state.toJson(), now);
                }
                return;
            }
            String validUntil = item.certificate.getValidUntil();
            Integer due = CertificateLogic.dueReminder(item.certificate, now.toLocalDate(), state.sentFor(validUntil));
            if (due == null) return;
            Set<String> addresses = MailRecipients.resolve(ds, objects(item.certificate.getResponsible()));
            if (addresses.isEmpty()) {
                if (!"No recipients with an e-mail address".equals(state.getLastError())) {
                    state.setLastError("No recipients with an e-mail address");
                    write(stateAttribute, state.toJson(), now);
                }
                result.failed++;
                return;
            }
            MailMessage mail = CertificateMails.reminder(item.certificate, item.building, due, now.toLocalDate(), webAppUrl).to(addresses);
            try {
                channels.mail(mail);
            } catch (Exception e) {
                state.setLastError(message(e));
                write(stateAttribute, state.toJson(), now);
                result.failed++;
                logger.error("Could not send the reminder of the energy certificate {}", item.object.getID(), e);
                return;
            }
            state.markSent(validUntil, due, now.toString("yyyy-MM-dd'T'HH:mm:ss"));
            write(stateAttribute, state.toJson(), now);
            result.reminders++;
            logger.info("Energy certificate reminder ({} months) sent for {} ({})", due, item.object.getName(), item.object.getID());
        } catch (Exception e) {
            result.failed++;
            logger.error("Could not process the reminder of {}", item.object, e);
        }
    }

    // ---------------------------------------------------------------- transfers

    void transfer(JEVisObject target, List<Item> items, DateTime now, Result result) {
        try {
            if (!"true".equalsIgnoreCase(text(target, "Enabled")) && !"1".equals(text(target, "Enabled"))) return;
            JEVisAttribute lastAttribute = target.getAttribute("Last Transfer");
            JEVisAttribute requestedAttribute = target.getAttribute("Requested");
            if (lastAttribute == null) return;
            TransferSchedule.LastTransfer last = TransferSchedule.LastTransfer.fromJson(latest(lastAttribute));
            String requestedText = requestedAttribute != null ? latest(requestedAttribute) : null;
            boolean requested = requestedText != null && !requestedText.trim().isEmpty();

            List<Item> scope = scope(target, items);
            String key = String.valueOf(target.getID());
            int changed = 0;
            for (Item item : scope) {
                CertificateState.Transfer t = state(item).getTransfers().get(key);
                if (t == null || !Objects.equals(t.getVersion(), item.version)) changed++;
            }
            TransferSchedule.Schedule schedule = TransferSchedule.Schedule.of(text(target, "Schedule"));
            if (!TransferSchedule.isDue(schedule, last, requested, changed, now)) return;

            CertificateExport.Format format = CertificateExport.Format.of(text(target, "Format"));
            List<Item> sent = new ArrayList<>();
            for (Item item : scope) {
                if (schedule == TransferSchedule.Schedule.ON_CHANGE && !requested) {
                    CertificateState.Transfer t = state(item).getTransfers().get(key);
                    if (t != null && Objects.equals(t.getVersion(), item.version)) continue;
                }
                sent.add(item);
            }
            String fileName = CertificateExport.fileName(format, now);
            try {
                byte[] content = CertificateExport.build(format, entries(sent, format), now.toLocalDate(), now);
                send(target, format, fileName, content, sent.size(), last);
                last.succeeded(now, sent.size());
                result.transfers++;
                for (Item item : sent) {
                    JEVisAttribute stateAttribute = item.object.getAttribute(EnergyCertificate.ATTRIBUTE_TRANSFER_STATE);
                    if (stateAttribute == null) continue;
                    CertificateState state = state(item);
                    state.getTransfers().put(key, new CertificateState.Transfer(now.toString(), item.version));
                    write(stateAttribute, state.toJson(), now);
                }
                logger.info("Energy certificates sent to {} ({}): {} certificate(s), {}", target.getName(), target.getID(), sent.size(), format);
            } catch (Exception e) {
                last.failed(now, message(e));
                result.failed++;
                logger.error("Could not send the energy certificates to {} ({})", target.getName(), target.getID(), e);
            }
            write(lastAttribute, last.toJson(), now);
            if (requested) write(requestedAttribute, "", now);
        } catch (Exception e) {
            result.failed++;
            logger.error("Could not process the transfer {}", target, e);
        }
    }

    private void send(JEVisObject target, CertificateExport.Format format, String fileName, byte[] content, int count,
                      TransferSchedule.LastTransfer last) throws Exception {
        String channel = text(target, "Channel");
        channel = channel != null ? channel.trim().toUpperCase(Locale.ROOT) : "";
        switch (channel) {
            case "MAIL": {
                Set<String> addresses = recipients(text(target, "Recipients"));
                if (addresses.isEmpty()) throw new IllegalStateException("No recipients with an e-mail address");
                channels.mail(CertificateMails.transfer(target.getName(), count, fileName, content, format.contentType).to(addresses));
                break;
            }
            case "SFTP": {
                String host = text(target, "Host");
                if (host == null || host.trim().isEmpty()) throw new IllegalStateException("No SFTP host");
                int port = 22;
                String portText = text(target, "Port");
                if (portText != null && !portText.trim().isEmpty()) port = Integer.parseInt(portText.trim());
                String key = channels.sftp(host.trim(), port, text(target, "User"), text(target, "Password"),
                        text(target, "Path"), last.getHostKey(), fileName, content);
                last.setHostKey(key);
                break;
            }
            case "HTTP": {
                String url = text(target, "URL");
                if (url == null || url.trim().isEmpty()) throw new IllegalStateException("No URL");
                channels.http(url.trim(), text(target, "Token"), fileName, content, format.contentType);
                break;
            }
            default:
                throw new IllegalStateException("Unknown channel '" + channel + "'");
        }
    }

    /** @return the certificates below the parent of the target (all, if it has none) */
    static List<Item> scope(JEVisObject target, List<Item> items) throws JEVisException {
        List<JEVisObject> parents = target.getParents();
        if (parents == null || parents.isEmpty()) return items;
        long parent = parents.get(0).getID();
        List<Item> scope = new ArrayList<>();
        for (Item item : items) if (item.ancestors.contains(parent)) scope.add(item);
        return scope;
    }

    private List<CertificateExport.Entry> entries(List<Item> items, CertificateExport.Format format) {
        List<CertificateExport.Entry> entries = new ArrayList<>();
        for (Item item : items) {
            String fileName = item.file != null ? stringValue(item.file) : null;
            byte[] pdf = null;
            if (fileName != null && format == CertificateExport.Format.ZIP) {
                try {
                    JEVisFile file = item.file.getValueAsFile();
                    pdf = file != null ? file.getBytes() : null;
                } catch (Exception e) {
                    logger.warn("Could not read the PDF of the energy certificate {}", item.object.getID(), e);
                }
            }
            entries.add(new CertificateExport.Entry(item.object.getID(), item.building, item.certificate, fileName, pdf));
        }
        return entries;
    }

    /**
     * @return the addresses of the recipient text: user/group ids and e-mail addresses separated by ";", "," or blanks
     */
    Set<String> recipients(String text) {
        Set<String> addresses = new LinkedHashSet<>();
        List<Long> ids = new ArrayList<>();
        for (String token : MailRecipients.parseAddresses(text)) {
            if (token.matches("\\d+")) ids.add(Long.parseLong(token));
            else if (token.contains("@")) addresses.add(token);
        }
        addresses.addAll(MailRecipients.resolve(ds, objects(ids)));
        return addresses;
    }

    // ---------------------------------------------------------------- reading

    Item item(JEVisObject object) throws JEVisException {
        JEVisAttribute data = object.getAttribute(EnergyCertificate.ATTRIBUTE_DATA);
        if (data == null) return null;
        ds.reloadAttribute(data);
        JEVisSample latest = data.getLatestSample();
        EnergyCertificate certificate = latest != null ? CertificateLogic.fromJson(latest.getValueAsString()) : null;
        if (certificate == null) return null;
        Item item = new Item();
        item.object = object;
        item.certificate = certificate;
        JEVisAttribute file = object.getAttribute(EnergyCertificate.ATTRIBUTE_FILE);
        if (file != null) {
            ds.reloadAttribute(file);
            item.file = file.getLatestSample();
        }
        DateTime version = latest.getTimestamp();
        if (item.file != null && item.file.getTimestamp() != null && item.file.getTimestamp().isAfter(version)) {
            version = item.file.getTimestamp();
        }
        item.version = version.toString();
        JEVisObject p = object;
        String building = null;
        for (int depth = 0; depth < 50; depth++) {
            List<JEVisObject> parents = p.getParents();
            if (parents == null || parents.isEmpty()) break;
            p = parents.get(0);
            item.ancestors.add(p.getID());
            if (building == null && (CLASS_BUILDING.equals(p.getJEVisClassName()) || CLASS_ORGANIZATION.equals(p.getJEVisClassName()))) {
                building = p.getName();
            }
        }
        item.building = building != null ? building : object.getName();
        return item;
    }

    private CertificateState state(Item item) throws JEVisException {
        return CertificateState.fromJson(latest(item.object.getAttribute(EnergyCertificate.ATTRIBUTE_TRANSFER_STATE)));
    }

    private List<JEVisObject> objectsOf(String className) {
        try {
            JEVisClass jeVisClass = ds.getJEVisClass(className);
            if (jeVisClass == null) return Collections.emptyList();
            List<JEVisObject> objects = ds.getObjects(jeVisClass, false);
            return objects != null ? objects : Collections.<JEVisObject>emptyList();
        } catch (Exception e) {
            logger.error("Could not load objects of class {}", className, e);
            return Collections.emptyList();
        }
    }

    private List<JEVisObject> objects(List<Long> ids) {
        List<JEVisObject> objects = new ArrayList<>();
        for (Long id : ids) {
            if (id == null) continue;
            try {
                JEVisObject object = ds.getObject(id);
                if (object != null) objects.add(object);
            } catch (Exception e) {
                logger.warn("Could not load the recipient {}", id, e);
            }
        }
        return objects;
    }

    private String text(JEVisObject object, String attribute) throws JEVisException {
        JEVisAttribute a = object.getAttribute(attribute);
        return a != null ? latest(a) : null;
    }

    private String latest(JEVisAttribute attribute) {
        if (attribute == null) return null;
        try {
            ds.reloadAttribute(attribute);
        } catch (Exception e) {
            logger.debug("Could not reload {}", attribute, e);
        }
        try {
            JEVisSample sample = attribute.getLatestSample();
            return sample != null ? sample.getValueAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringValue(JEVisSample sample) {
        try {
            return sample.getValueAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(JEVisAttribute attribute, String value, DateTime now) throws JEVisException {
        /* one sample per second: a second write in the same cycle replaces the first */
        JEVisSample sample = attribute.buildSample(now.withMillisOfSecond(0), value);
        sample.commit();
    }

    private static String message(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
