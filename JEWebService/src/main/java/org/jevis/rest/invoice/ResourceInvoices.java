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
package org.jevis.rest.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTimeZone;

import javax.security.sasl.AuthenticationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Import of electronic energy invoices (ZUGFeRD / Factur-X, XRechnung), see docs/ENERGY-INVOICES.md:
 * {@code POST /invoices/parse} reads a file without storing it, {@code POST /invoices/import} stores it below an
 * Energy Invoices Directory.
 */
@Path("/JEWebService/v1/invoices")
public class ResourceInvoices {

    /** largest accepted file */
    public static final int MAX_FILE_SIZE = 20 * 1024 * 1024;
    private static final Logger logger = LogManager.getLogger(ResourceInvoices.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @POST
    @Path("parse")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response parse(@Context HttpHeaders httpHeaders,
                          @QueryParam("directory") Long directory,
                          @FormDataParam("file") FormDataBodyPart file) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            byte[] bytes = read(file);
            return parse(bytes, fileName(file), directory, new SqlInvoiceStore(ds));
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (InvoiceException ex) {
            return error(ex);
        } catch (Exception ex) {
            logger.error("Could not parse the invoice", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @POST
    @Path("import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importInvoice(@Context HttpHeaders httpHeaders,
                                  @QueryParam("directory") Long directory,
                                  @QueryParam("medium") String medium,
                                  @FormDataParam("file") FormDataBodyPart file) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            byte[] bytes = read(file);
            return importInvoice(bytes, fileName(file), directory, medium, new SqlInvoiceStore(ds), DateTimeZone.getDefault());
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (InvoiceException ex) {
            return error(ex);
        } catch (Exception ex) {
            logger.error("Could not import the invoice", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    /**
     * Parse answer: the invoice JSON with {@code duplicate} if the directory is a readable Energy Invoices Directory.
     */
    static Response parse(byte[] bytes, String fileName, Long directory, InvoiceStore store) throws Exception {
        try {
            EInvoice invoice = InvoiceReader.read(bytes);
            InvoiceAnalysis.Analysis analysis = InvoiceAnalysis.analyse(invoice);
            Long duplicate = null;
            if (directory != null && InvoiceImport.directory(store, directory) != null) {
                duplicate = InvoiceImport.findDuplicate(store, directory, invoice);
            }
            return json(Response.Status.OK, InvoiceAnalysis.toJson(invoice, analysis, fileName, duplicate));
        } catch (InvoiceException ex) {
            return error(ex);
        }
    }

    /**
     * Import answer (201): {@code {invoiceId, folderId, series, warnings}}.
     */
    static Response importInvoice(byte[] bytes, String fileName, Long directory, String mediumName, InvoiceStore store,
                                  DateTimeZone zone) throws Exception {
        try {
            Medium medium = Medium.parse(mediumName);
            if (medium == null) {
                throw new InvoiceException(400, InvoiceException.INVALID_MEDIUM,
                        "The medium must be one of ELECTRICITY, GAS, DISTRICT_HEATING, OTHER.");
            }
            if (InvoiceImport.directory(store, directory) == null) {
                throw new InvoiceException(400, InvoiceException.INVALID_DIRECTORY,
                        "The directory is no readable Energy Invoices Directory.");
            }
            EInvoice invoice = InvoiceReader.read(bytes);
            InvoiceAnalysis.Analysis analysis = InvoiceAnalysis.analyse(invoice);
            InvoiceImport.Result result = InvoiceImport.importInvoice(store, directory, medium, invoice, analysis, bytes,
                    fileName, zone);
            return json(Response.Status.CREATED, result.toJson());
        } catch (InvoiceException ex) {
            return error(ex);
        }
    }

    /**
     * @return the content of the uploaded file
     * @throws InvoiceException 400 without file, 413 if it is larger than {@link #MAX_FILE_SIZE}
     */
    static byte[] read(FormDataBodyPart file) throws InvoiceException, IOException {
        if (file == null) throw new InvoiceException(400, InvoiceException.NO_FILE, "The request contains no file.");
        try (InputStream in = file.getValueAs(InputStream.class)) {
            return read(in, MAX_FILE_SIZE);
        }
    }

    /**
     * @throws InvoiceException 413 if the stream has more than {@code max} bytes
     */
    static byte[] read(InputStream in, int max) throws InvoiceException, IOException {
        if (in == null) throw new InvoiceException(400, InvoiceException.NO_FILE, "The request contains no file.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > max) {
                throw new InvoiceException(413, InvoiceException.TOO_LARGE,
                        "The file is larger than " + (max / (1024 * 1024)) + " MB.");
            }
        }
        return out.toByteArray();
    }

    private static String fileName(FormDataBodyPart file) {
        if (file == null || file.getContentDisposition() == null) return null;
        return file.getContentDisposition().getFileName();
    }

    static Response error(InvoiceException ex) {
        try {
            return json(Response.Status.fromStatusCode(ex.getStatus()), ex.toJson());
        } catch (Exception e) {
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }
    }

    private static Response json(Response.Status status, Map<String, Object> json) throws Exception {
        return Response.status(status).entity(MAPPER.writeValueAsString(json)).type(MediaType.APPLICATION_JSON_TYPE).build();
    }
}
