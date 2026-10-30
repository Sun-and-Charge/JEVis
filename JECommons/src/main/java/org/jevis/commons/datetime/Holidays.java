/**
 * Copyright (C) JEVis contributors
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
package org.jevis.commons.datetime;

import de.jollyday.HolidayManager;
import de.jollyday.ManagerParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.i18n.I18n;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

/**
 * Application wide holiday managers (default locale + all buildings), moved from the Control Center.
 * For a single building see {@link HolidayCalendar}.
 */
public class Holidays {
    private static final Logger logger = LogManager.getLogger(Holidays.class);
    private static JEVisDataSource ds;
    private static JEVisClass siteClass;
    private static HolidayManager defaultHolidayManager;
    private static final HashMap<JEVisObject, HolidayManager> siteHolidayManagerMap = new HashMap<>();
    private static final HashMap<JEVisObject, HolidayManager> customHolidayManagerMap = new HashMap<>();
    private static String stateCode = "";

    public Holidays() {
    }

    public static void setDataSource(JEVisDataSource ds) {
        Holidays.ds = ds;
        if (ds != null) {
            try {
                siteClass = ds.getJEVisClass(HolidayCalendar.BUILDING_CLASS);
            } catch (JEVisException e) {
                logger.fatal("Could not get JEVisClass for Building");
            }
        }

        try {
            defaultHolidayManager = HolidayManager.getInstance(ManagerParameters.create(I18n.getInstance().getLocale()));
        } catch (Exception e) {
            defaultHolidayManager = HolidayManager.getInstance(ManagerParameters.create(I18n.getInstance().getDefaultBundle().getLocale()));
            logger.error("Could not identify {}, creating fallback default holiday manager {}", I18n.getInstance().getLocale(), I18n.getInstance().getDefaultBundle().getLocale());
        }
        if (ds != null) {
            try {
                List<JEVisObject> sites = ds.getObjects(siteClass, false);
                for (JEVisObject site : sites) {
                    JEVisAttribute holidaysAtt = site.getAttribute(HolidayCalendar.HOLIDAYS_ATTRIBUTE);
                    if (holidaysAtt.hasSample()) {
                        try {
                            String[] codes = HolidayCalendar.parseCountryAndState(holidaysAtt.getLatestSample().getValueAsString());
                            if (codes != null) {
                                siteHolidayManagerMap.put(site, HolidayManager.getInstance(ManagerParameters.create(codes[0])));
                                stateCode = codes[1];
                            }
                        } catch (Exception e) {
                            logger.error("Could not create site holiday manager");
                        }
                    }

                    JEVisAttribute customHolidaysAtt = site.getAttribute(HolidayCalendar.CUSTOM_HOLIDAYS_ATTRIBUTE);
                    if (customHolidaysAtt.hasSample()) {
                        try {
                            JEVisFile jeVisFile = customHolidaysAtt.getLatestSample().getValueAsFile();
                            String property = System.getProperty("java.io.tmpdir");
                            File file = new File(property + "/" + jeVisFile.getFilename());
                            file.deleteOnExit();
                            jeVisFile.saveToFile(file);

                            URL url = new URL("file:" + file);
                            customHolidayManagerMap.put(site, HolidayManager.getInstance(ManagerParameters.create(url)));
                        } catch (Exception e) {
                            logger.error("Could not create custom holiday manager");
                        }
                    }
                }
            } catch (Exception e) {
                logger.fatal("Could not create any custom holiday manager");
            }
        }
    }

    public static String getStateCode() {
        return stateCode;
    }

    public static HolidayManager getDefaultHolidayManager() {
        return defaultHolidayManager;
    }

    public static HolidayManager getSiteHolidayManager(JEVisObject site) {
        if (site != null) {
            return siteHolidayManagerMap.get(site);
        } else return defaultHolidayManager;
    }

    public static HolidayManager getCustomHolidayManager(JEVisObject site) {
        if (site != null) {
            return customHolidayManagerMap.get(site);
        } else return defaultHolidayManager;
    }
}
