/**
 * Copyright (C) JEVis contributors
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
package org.jevis.iso.rest;

import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.iso.add.TemplateChooser;
import org.jevis.iso.add.Translations;
import org.jevis.iso.classes.DocumentsDirectory;
import org.jevis.iso.classes.ExternalAudit;
import org.jevis.iso.classes.ISO50001;
import org.jevis.iso.classes.InternalAudit;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
@Path("/JEWebService/v1/audits")
public class Audits {

    /**
     * @param httpHeaders
     * @param site
     * @param lang
     * @return
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getObject(
            @Context HttpHeaders httpHeaders,
            @Context Request request,
            @Context UriInfo url,
            @DefaultValue("") @QueryParam("site") String site,
            @DefaultValue("") @QueryParam("lang") String lang
    ) throws Exception {
        SQLDataSource ds = null;
        try {

            ds = new SQLDataSource(httpHeaders, request, url);
            ds.preload(SQLDataSource.PRELOAD.ALL_OBJECT);
            ds.preload(SQLDataSource.PRELOAD.ALL_REL);

            ISO50001 iso = new ISO50001(ds);

            Map<String, Object> root = new HashMap<>();

            root.put("bauth", httpHeaders.getRequestHeader("authorization").get(0));

            DocumentsDirectory doc = iso.getOrganisation().getSite(site).getDocuments();

            List<ExternalAudit> listExternalAudits = doc.getExternalAudits();
            List<InternalAudit> listInternalAudits = doc.getInternalAudits();
            for (InternalAudit ia : listInternalAudits) {
                listInternalAudits.get(listInternalAudits.indexOf(ia)).buildGeneralQuestions();
                listInternalAudits.get(listInternalAudits.indexOf(ia)).buildPlanQuestions();
                listInternalAudits.get(listInternalAudits.indexOf(ia)).buildDoQuestions();
                listInternalAudits.get(listInternalAudits.indexOf(ia)).buildCheckQuestions();
                listInternalAudits.get(listInternalAudits.indexOf(ia)).buildActQuestions();
            }

            List<Long> listEAYears = doc.getListYearsExternalAudits();
            List<Long> listIAYears = doc.getListYearsInternalAudits();

            root.put("AuditsDirectoryName", doc.getAuditsDirName());
            root.put("AuditsDirectoryID", doc.getAuditsDirID());
            root.put("externalaudits", listExternalAudits);
            root.put("listEAYears", listEAYears);
            root.put("internalaudits", listInternalAudits);
            root.put("listIAYears", listIAYears);
            root.put("siteName", site);
            if (!lang.equals("")) {
                Translations t = new Translations();
                root.put("externalAudits", t.getTranslatedKey(lang, "External Audits"));
                root.put("internalAudits", t.getTranslatedKey(lang, "Internal Audits"));
                root.put("addAuditQuestion", t.getTranslatedKey(lang, "Add Audit Question"));
                root.put("addExternalAudit", t.getTranslatedKey(lang, "Add External Audit"));
                root.put("addInternalAudit", t.getTranslatedKey(lang, "Add Internal Audit"));
                root.put("removeAudit", t.getTranslatedKey(lang, "Remove Audit"));
            } else {
                root.put("addExternalAudit", "Add External Audit");
                root.put("addInternalAudit", "Add Internal Audit");
                root.put("externalAudits", "External Audits");
                root.put("internalAudits", "Internal Audits");
                root.put("addAuditQuestion", "Add Audit Question");
                root.put("removeAudit", "Remove Audit");
            }

            TemplateChooser tc = new TemplateChooser(root, "audits");

            return Response.ok(tc.getOutput()).build();
        } finally {
            Config.CloseDS(ds);
        }
    }
}
