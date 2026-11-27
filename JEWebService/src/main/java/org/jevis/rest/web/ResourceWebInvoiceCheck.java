/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.mail.MailMessage;
import org.jevis.commons.mail.MailSender;
import org.jevis.commons.mail.MailSettings;
import org.jevis.commons.trc.ResultCalculator;
import org.jevis.commons.trc.TrcResult;
import org.jevis.commons.trc.TrcTimeFrames;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.local.LocalDataSource;
import org.jevis.commons.ws.sql.mail.ServerMail;
import org.jevis.rest.invoice.InvoiceImport;
import org.jevis.rest.invoice.SqlInvoiceStore;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import javax.security.sasl.AuthenticationException;
import java.util.*;

/**
 * {@code POST /web/invoices/{id}/check} (R03): checks the invoice again ({@link InvoiceCheck}), with write right on
 * it; {@link #afterImport} runs the check after an import or a manual invoice.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebInvoiceCheck {

    private static final Logger logger = LogManager.getLogger(ResourceWebInvoiceCheck.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @POST
    @Path("invoices/{id}/check")
    @Produces(MediaType.APPLICATION_JSON)
    public Response check(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            JsonObject invoice = ds.getObject(id);
            if (invoice == null || !InvoiceImport.CLASS_INVOICE.equals(invoice.getJevisClass()) || ResourceWebIso.isDeleted(invoice)) {
                return Response.status(Response.Status.NOT_FOUND).entity("Invoice not found").build();
            }
            if (!ds.getUserManager().canWrite(invoice)) {
                return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to check this invoice").build();
            }
            Map<String, Object> result = InvoiceCheck.check(new SqlEnv(ds), id, DateTime.now());
            return Response.ok(MAPPER.writeValueAsString(result)).type(MediaType.APPLICATION_JSON).build();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not check the invoice {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    /**
     * Runs the check after an import; never throws (the import has succeeded either way).
     *
     * @return the result JSON, null if the check could not run at all
     */
    public static Map<String, Object> afterImport(SQLDataSource ds, long invoiceId) {
        try {
            return InvoiceCheck.check(new SqlEnv(ds), invoiceId, DateTime.now());
        } catch (Exception e) {
            logger.warn("Invoice check after the import of {} failed", invoiceId, e);
            return null;
        }
    }

    /** the check against the database, with the rights of the user */
    static final class SqlEnv implements InvoiceCheck.Env {
        private final SQLDataSource ds;
        private final SqlInvoiceStore store;
        private final DateTimeZone zone = DateTimeZone.getDefault();
        private ResourceWebModules.Lookup lookup;
        private ResourceWebContracts.Store contracts;

        SqlEnv(SQLDataSource ds) {
            this.ds = ds;
            this.store = new SqlInvoiceStore(ds);
        }

        private ResourceWebModules.Lookup lookup() throws Exception {
            if (lookup == null) lookup = ResourceWebModules.lookup(ds);
            return lookup;
        }

        private ResourceWebContracts.Store contractStore() {
            if (contracts == null) contracts = ResourceWebContracts.sqlStore(ds);
            return contracts;
        }

        @Override
        public InvoiceCheck.Invoice invoice(long id) throws Exception {
            InvoiceCheck.Invoice invoice = new InvoiceCheck.Invoice();
            invoice.id = id;
            invoice.number = store.latest(id, "Invoice Number");
            invoice.seller = store.latest(id, "Seller");
            invoice.meterPoint = store.latest(id, "Meter Point");
            invoice.periodStart = InvoiceCheck.date(store.latest(id, "Period Start"), zone);
            invoice.periodEnd = InvoiceCheck.date(store.latest(id, "Period End"), zone);
            invoice.consumption = InvoiceCheck.number(store.latest(id, "Consumption"));
            invoice.consumptionUnit = store.unit(id, "Consumption");
            invoice.net = InvoiceCheck.number(store.latest(id, "Net Amount"));
            invoice.previousStatus = InvoiceCheck.status(store.latest(id, InvoiceCheck.ATTRIBUTE_RESULT));
            return invoice;
        }

        @Override
        public List<InvoiceCheck.Contract> contracts() throws Exception {
            List<InvoiceCheck.Contract> list = new ArrayList<>();
            ResourceWebRegisters.Classes classes = ResourceWebRegisters.Classes.fromCache();
            for (JsonObject object : ResourceWebRegisters.readableOfClasses(lookup(), classes.withHeirs(ResourceWebContracts.CLASS_CONFIGURATION))) {
                if (ResourceWebIso.isDeleted(object)) continue;
                String location = ResourceWebContracts.text(ResourceWebContracts.template(object.getId(), contractStore()), "marketLocationNumber");
                if (location != null) list.add(new InvoiceCheck.Contract(object.getId(), object.getName(), location));
            }
            return list;
        }

        @Override
        public Map<String, Double> calculate(long configuration, DateTime from, DateTime to) throws Exception {
            ResourceWebCosts.Subject subject = ResourceWebCosts.subject(lookup(), ResourceWebRegisters.Classes.fromCache(),
                    contractStore(), configuration);
            ResourceWebCosts.bind(subject, new HashMap<>());
            TrcResult result = new ResultCalculator(new LocalDataSource(ds), new TrcTimeFrames(zone, null)).calculate(subject.template, from, to);
            return result.getFormulaResults();
        }

        @Override
        public InvoiceCheck.Settings settings(long invoiceId) throws Exception {
            InvoiceCheck.Settings settings = new InvoiceCheck.Settings();
            boolean quantity = false, amount = false, recipients = false, group = false;
            Set<Long> visited = new HashSet<>();
            Long current = lookup().parent(invoiceId);
            while (current != null && visited.add(current)) {
                JsonObject folder = lookup().object(current);
                if (folder == null || !InvoiceImport.CLASS_DIRECTORY.equals(folder.getJevisClass())) break;
                Double q = InvoiceCheck.number(store.latest(current, InvoiceCheck.ATTRIBUTE_TOLERANCE_QUANTITY));
                if (!quantity && q != null && q >= 0) {
                    settings.toleranceQuantity = q;
                    quantity = true;
                }
                Double a = InvoiceCheck.number(store.latest(current, InvoiceCheck.ATTRIBUTE_TOLERANCE_AMOUNT));
                if (!amount && a != null && a >= 0) {
                    settings.toleranceAmount = a;
                    amount = true;
                }
                String r = store.latest(current, InvoiceCheck.ATTRIBUTE_RECIPIENTS);
                if (!recipients && r != null && !r.trim().isEmpty()) {
                    settings.recipients = r.trim();
                    recipients = true;
                }
                String g = store.latest(current, InvoiceCheck.ATTRIBUTE_RECIPIENT_GROUP);
                Long[] id = new Long[1];
                if (!group && g != null && ResourceWebRegisters.parseTarget(g, id, new String[1])) {
                    settings.recipientGroup = id[0];
                    group = true;
                }
                current = lookup().parent(current);
            }
            return settings;
        }

        @Override
        public void store(long invoiceId, String json, DateTime now) throws Exception {
            store.setSample(invoiceId, InvoiceCheck.ATTRIBUTE_RESULT, JEVisConstants.PrimitiveType.STRING, now, json, null);
        }

        @Override
        public boolean mail(InvoiceCheck.Settings settings, InvoiceCheck.Invoice invoice, Map<String, Object> result) throws Exception {
            MailSettings mail = ServerMail.findDefaultSettings(ds);
            if (mail == null || !mail.isValid()) return false;
            Set<String> to = new LinkedHashSet<>();
            if (settings.recipients != null) {
                for (String address : settings.recipients.split("[;,\\s]+")) {
                    if (address.contains("@")) to.add(address.trim());
                }
            }
            /* group members as blind copy, so they do not see each other's addresses */
            Set<String> members = new LinkedHashSet<>();
            if (settings.recipientGroup != null) {
                JsonObject group = ds.getObject(settings.recipientGroup);
                if (group != null) members.addAll(ServerMail.resolve(ds, Collections.singletonList(group)));
            }
            members.removeAll(to);
            if (to.isEmpty() && members.isEmpty()) return false;
            MailMessage message = new MailMessage().to(to).bcc(members)
                    .subject("Rechnungsprüfung: Abweichung bei Rechnung " + (invoice.number != null ? invoice.number : "#" + invoice.id))
                    .text(InvoiceCheckMail.html(invoice, result), true);
            new MailSender(mail).send(message);
            return true;
        }
    }
}
