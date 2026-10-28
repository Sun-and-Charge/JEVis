/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisSample;
import org.jevis.jeconfig.application.jevistree.plugin.MapPlugin;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author broder
 */
public class DataCalculator {
    private static final Logger logger = LogManager.getLogger(DataCalculator.class);

    public List<GPSRoute> calcRoues(Set<MapPlugin.DataModel> routesRaw) {
        List<GPSRoute> routes = new ArrayList<>();
        for (MapPlugin.DataModel currentRoute : routesRaw) {

            DateTime selectedEnd = currentRoute.getSelectedEnd();
            DateTime selectedStart = currentRoute.getSelectedStart();

            List<JEVisSample> latSamples = currentRoute.getLatitudeAttribute().getSamples(selectedStart, selectedEnd);
            List<JEVisSample> longSamples = currentRoute.getLongitudeAttribute().getSamples(selectedStart, selectedEnd);
            try {
                GPSRoute gpsRoute = getGPSSamples(latSamples, longSamples);
                gpsRoute.setColor(currentRoute.getColor());
                gpsRoute.setName(currentRoute.getObject().getParents().get(0).getName());
                routes.add(gpsRoute);
            } catch (JEVisException ex) {
                logger.fatal(ex);
            }
        }
        return routes;
    }

    private GPSRoute getGPSSamples(List<JEVisSample> latSamples, List<JEVisSample> longSamples) throws JEVisException {
        GPSRoute route = new GPSRoute();
        if (latSamples.size() != longSamples.size()) {
            logger.info("long and last samplelist differ in length");
            return route;
        }
        List<GPSSample> gpsSamples = new ArrayList<>();
        for (int i = 0; i < latSamples.size(); i++) {
            DateTime timestamp = latSamples.get(i).getTimestamp();
            Double latValue = latSamples.get(i).getValueAsDouble();
            Double longValue = longSamples.get(i).getValueAsDouble();
            GPSSample sample = new GPSSample(latValue, longValue, timestamp);
            gpsSamples.add(sample);
        }
        route.setGpsSample(gpsSamples);
        return route;
    }
}
