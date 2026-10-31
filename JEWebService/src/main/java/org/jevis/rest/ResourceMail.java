/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.mail.MailMessage;
import org.jevis.commons.mail.MailSettings;
import org.jevis.commons.mail.ReplyToMailSender;
import org.jevis.commons.ws.json.JsonMailRequest;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.JEVisUserSQL;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.mail.MailRequestValidator;
import org.jevis.commons.ws.sql.mail.ServerMail;

import javax.mail.MessagingException;
import javax.security.sasl.AuthenticationException;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.*;

/**
 * Sends exports (analyses, dashboards, ...) by mail with the system mail settings (OpenJEVis #2234).
 *
 * <pre>
 * POST /JEWebService/v1/mail/send  JsonMailRequest + Bearer -> {"recipients": n}
 * </pre>
 * <p>
 * Recipients are e-mail addresses ({@code to}) and users/groups ({@code userIds}, {@code groupIds}) the caller can
 * read; a group stands for the e-mail addresses of its members. The sender is the account of the default "EMail
 * Plugin", Reply-To is the e-mail address of the calling user. Limits see {@link MailRequestValidator}.
 * <p>
 * Answers: 200 sent, 400 invalid request (message as text), 401 not logged in, 403 API key, 413 too large,
 * 502 the mail server rejected the mail, 503 no mail server configured.
 */
@Path("/JEWebService/v1/mail")
public class ResourceMail {

    private static final Logger logger = LogManager.getLogger(ResourceMail.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private SQLDataSource ds;

    @POST
    @Logged
    @Path("/send")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response send(@Context HttpHeaders httpHeaders, @Context Request request, @Context UriInfo url, String body) {
        try {
            ds = new SQLDataSource(httpHeaders, request, url);
            if (ds.getAuthMethod() == SQLDataSource.AuthMethod.API_KEY) {
                return Response.status(Response.Status.FORBIDDEN).entity("Not allowed for API keys").build();
            }
            if (body == null || body.trim().isEmpty()) {
                return badRequest("The request is empty");
            }
            if (body.length() > MailRequestValidator.MAX_BODY_CHARS) {
                return Response.status(413).entity("The attachments are larger than "
                        + (MailRequestValidator.MAX_ATTACHMENT_BYTES / 1024 / 1024) + " MB").build();
            }
            JsonMailRequest mailRequest;
            try {
                mailRequest = MAPPER.readValue(body, JsonMailRequest.class);
            } catch (Exception ex) {
                return badRequest("Invalid JSON");
            }

            /* the sender is the default EMail Plugin */
            MailSettings settings = ServerMail.findDefaultSettings(ds);
            if (settings == null || !settings.isValid()) {
                logger.warn("Mail requested by '{}', but no mail server is configured", ds.getCurrentUser().getAccountName());
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("No mail server is configured").build();
            }

            Set<String> typed = new LinkedHashSet<>(MailRequestValidator.explicitAddresses(mailRequest));
            List<JsonObject> objects = new ArrayList<>();
            addReadable(objects, mailRequest.getUserIds(), ServerMail.USER_CLASS);
            addReadable(objects, mailRequest.getGroupIds(), ServerMail.GROUP_CLASS);
            Set<String> resolved = new LinkedHashSet<>(ServerMail.resolve(ds, objects));
            Set<String> recipients = new LinkedHashSet<>(typed);
            recipients.addAll(resolved);

            /* users and groups as blind copy, so group members do not see each other's addresses */
            MailMessage message = MailRequestValidator.toMessage(mailRequest, typed, resolved);

            JEVisUserSQL user = ds.getCurrentUser();
            String replyTo = String.join(";", ServerMail.addressesOfUser(ds, user.getUserID()));
            new ReplyToMailSender(settings, replyTo).send(message);

            logger.info("User '{}' sent mail '{}' with {} attachment(s) ({} bytes) to {} recipient(s)",
                    user.getAccountName(), message.getSubject(), message.getAttachments().size(),
                    MailRequestValidator.attachmentBytes(message), recipients.size());
            Map<String, Object> answer = new LinkedHashMap<>();
            answer.put("recipients", recipients.size());
            return Response.ok(MAPPER.writeValueAsString(answer)).type(MediaType.APPLICATION_JSON).build();
        } catch (MailRequestValidator.InvalidMailRequestException ex) {
            return badRequest(ex.getMessage());
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (MessagingException ex) {
            logger.error("Could not send mail of '{}': {}", ds != null ? ds.getCurrentUser().getAccountName() : "?", ex.getMessage(), ex);
            return Response.status(Response.Status.BAD_GATEWAY).entity("The mail server did not accept the mail").build();
        } catch (Exception ex) {
            logger.error("Mail send failed", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    /**
     * Adds the users or groups the caller can read; unknown, unreadable or wrong objects are a bad request.
     */
    private void addReadable(List<JsonObject> objects, List<Long> ids, String jevisClass) throws Exception {
        if (ids == null) return;
        for (Long id : ids) {
            if (id == null) continue;
            JsonObject object = ds.getObject(id);
            boolean readable;
            try {
                readable = object != null && ds.getUserManager().canRead(object);
            } catch (Exception ex) {
                readable = false;
            }
            if (!readable || !jevisClass.equals(object.getJevisClass())) {
                throw new MailRequestValidator.InvalidMailRequestException("Unknown " + jevisClass.toLowerCase(Locale.ROOT) + ": " + id);
            }
            objects.add(object);
        }
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(message).build();
    }
}
