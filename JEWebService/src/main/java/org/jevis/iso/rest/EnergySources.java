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
import org.jevis.iso.classes.EnergyPlanningDirectory;
import org.jevis.iso.classes.EnergySource;
import org.jevis.iso.classes.ISO50001;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
@Path("/JEWebService/v1/energysources")
public class EnergySources {

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

            EnergyPlanningDirectory epd = iso.getOrganisation().getSite(site).getenergyplanning();

            List<EnergySource> listEnergySources = epd.getEnergySources();
            for (EnergySource es : listEnergySources) {
                listEnergySources.get(listEnergySources.indexOf(es)).getEnergyconsumptions();
                listEnergySources.get(listEnergySources.indexOf(es)).getEnergyBills();
            }
            long EnergySourcesDirID = epd.getEnergySourcesDirID();
            String EnergySourcesDirName = epd.getEnergySourcesDirName();
            Map<String, Object> root = new HashMap<>();

            root.put("bauth", httpHeaders.getRequestHeader("authorization").get(0));

            root.put("EnergySourcesDirID", EnergySourcesDirID);
            root.put("EnergySourcesDirName", EnergySourcesDirName);
            root.put("energysources", listEnergySources);
            root.put("siteName", site);
            if (!lang.equals("")) {
                Translations t = new Translations();
                root.put("addEnergySource", t.getTranslatedKey(lang, "Add Energy Source"));
                root.put("removeEnergySource", t.getTranslatedKey(lang, "Remove Energy Source"));
                root.put("addEnergyConsumptionBills", t.getTranslatedKey(lang, "Add Energy Consumption / Bills"));
                root.put("esoverview", t.getTranslatedKey(lang, "Overview"));
            } else {
                root.put("addEnergySource", "Add Energy Source");
                root.put("removeEnergySource", "Remove Energy Source");
                root.put("addEnergyConsumptionBills", "Add Energy Consumption / Bills");
                root.put("esoverview", "Overview");
            }

            TemplateChooser tc = new TemplateChooser(root, "energysources");

            return Response.ok(tc.getOutput()).build();
        } finally {
            Config.CloseDS(ds);
        }
    }
}
