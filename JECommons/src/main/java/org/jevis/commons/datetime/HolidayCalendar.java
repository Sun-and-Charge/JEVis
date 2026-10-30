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
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisFile;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.utils.CommonMethods;
import org.joda.time.DateTime;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Public and custom holidays of one building (JEVis class "Building", attributes "Holidays" and
 * "Custom Holidays"). Used by data processing (gap filling, forecast) to treat holidays as their own
 * day class, see {@link DayClass}.
 */
public class HolidayCalendar implements Predicate<LocalDate> {
    public static final String BUILDING_CLASS = "Building";
    public static final String HOLIDAYS_ATTRIBUTE = "Holidays";
    public static final String CUSTOM_HOLIDAYS_ATTRIBUTE = "Custom Holidays";
    private static final Logger logger = LogManager.getLogger(HolidayCalendar.class);
    /**
     * building id -> calendar, invalidated when one of the holiday attributes gets a newer sample
     */
    private static final Map<Long, CachedCalendar> CACHE = new ConcurrentHashMap<>();

    private final HolidayManager siteManager;
    private final String stateCode;
    private final HolidayManager customManager;
    private final Map<LocalDate, Boolean> dayCache = new ConcurrentHashMap<>();

    public HolidayCalendar(HolidayManager siteManager, String stateCode, HolidayManager customManager) {
        this.siteManager = siteManager;
        this.stateCode = stateCode != null ? stateCode : "";
        this.customManager = customManager;
    }

    /**
     * Holiday calendar of the building above the given object, or {@code null} if there is no building
     * or the building has neither "Holidays" nor "Custom Holidays" configured.
     */
    public static HolidayCalendar forObject(JEVisObject object) {
        if (object == null) return null;
        try {
            JEVisObject building = CommonMethods.getFirstParentalObjectOfClass(object, BUILDING_CLASS);
            if (building == null || !BUILDING_CLASS.equals(building.getJEVisClassName())) {
                return null;
            }
            return forBuilding(building);
        } catch (Exception e) {
            logger.warn("Could not determine holidays for object {}:{}", object.getName(), object.getID(), e);
            return null;
        }
    }

    /**
     * Holiday calendar of the given building, or {@code null} if no holidays are configured.
     */
    public static HolidayCalendar forBuilding(JEVisObject building) {
        if (building == null) return null;
        try {
            JEVisSample holidaysSample = latestSample(building.getAttribute(HOLIDAYS_ATTRIBUTE));
            JEVisSample customSample = latestSample(building.getAttribute(CUSTOM_HOLIDAYS_ATTRIBUTE));
            if (holidaysSample == null && customSample == null) {
                CACHE.remove(building.getID());
                return null;
            }

            String version = timestampKey(holidaysSample) + "/" + timestampKey(customSample);
            CachedCalendar cached = CACHE.get(building.getID());
            if (cached != null && cached.version.equals(version)) {
                return cached.calendar;
            }

            HolidayManager siteManager = null;
            String stateCode = "";
            if (holidaysSample != null) {
                String[] codes = parseCountryAndState(holidaysSample.getValueAsString());
                if (codes != null) {
                    siteManager = createCountryManager(codes[0]);
                    stateCode = codes[1];
                }
            }

            HolidayManager customManager = null;
            if (customSample != null) {
                customManager = createCustomManager(customSample.getValueAsFile(),
                        "jevis-holidays-" + building.getID() + "-" + timestampKey(customSample));
            }

            HolidayCalendar calendar = siteManager == null && customManager == null ? null
                    : new HolidayCalendar(siteManager, stateCode, customManager);
            if (calendar != null) {
                CACHE.put(building.getID(), new CachedCalendar(version, calendar));
            } else {
                CACHE.remove(building.getID());
            }
            return calendar;
        } catch (Exception e) {
            logger.warn("Could not create holiday calendar for building {}:{}", building.getName(), building.getID(), e);
            return null;
        }
    }

    private static JEVisSample latestSample(JEVisAttribute attribute) {
        try {
            if (attribute != null && attribute.hasSample()) {
                return attribute.getLatestSample();
            }
        } catch (Exception e) {
            logger.debug("Could not read latest sample of {}", attribute, e);
        }
        return null;
    }

    private static String timestampKey(JEVisSample sample) {
        try {
            if (sample != null && sample.getTimestamp() != null) {
                return String.valueOf(sample.getTimestamp().getMillis());
            }
        } catch (Exception ignored) {
        }
        return "-";
    }

    /**
     * Parses the value of the building attribute "Holidays", e.g. {@code "DE,nw"} or {@code "DE"}.
     *
     * @return {countryCode, stateCode} or {@code null} if the value is empty
     */
    public static String[] parseCountryAndState(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String[] parts = value.split(",");
        String country = parts[0].trim();
        if (country.isEmpty()) return null;
        String state = parts.length > 1 ? parts[1].trim() : "";
        return new String[]{country, state};
    }

    public static HolidayManager createCountryManager(String countryCode) {
        try {
            return HolidayManager.getInstance(ManagerParameters.create(countryCode));
        } catch (Exception e) {
            logger.error("Could not create holiday manager for country {}", countryCode, e);
            return null;
        }
    }

    /**
     * Creates a jollyday manager from a custom holiday XML file. The file is stored in the temp
     * directory under the given base name.
     */
    public static HolidayManager createCustomManager(JEVisFile jeVisFile, String baseName) {
        if (jeVisFile == null) return null;
        try {
            File file = new File(System.getProperty("java.io.tmpdir"), baseName + ".xml");
            file.deleteOnExit();
            jeVisFile.saveToFile(file);
            URL url = file.toURI().toURL();
            return HolidayManager.getInstance(ManagerParameters.create(url));
        } catch (Exception e) {
            logger.error("Could not create custom holiday manager", e);
            return null;
        }
    }

    public static LocalDate toLocalDate(DateTime dateTime) {
        return LocalDate.of(dateTime.getYear(), dateTime.getMonthOfYear(), dateTime.getDayOfMonth());
    }

    public boolean isHoliday(LocalDate date) {
        if (date == null) return false;
        return dayCache.computeIfAbsent(date, this::computeHoliday);
    }

    public boolean isHoliday(DateTime dateTime) {
        return dateTime != null && isHoliday(toLocalDate(dateTime));
    }

    private boolean computeHoliday(LocalDate date) {
        try {
            if (siteManager != null) {
                boolean holiday = stateCode.isEmpty() ? siteManager.isHoliday(date) : siteManager.isHoliday(date, stateCode);
                if (holiday) return true;
            }
            return customManager != null && customManager.isHoliday(date);
        } catch (Exception e) {
            logger.debug("Could not check holiday for {}", date, e);
            return false;
        }
    }

    @Override
    public boolean test(LocalDate date) {
        return isHoliday(date);
    }

    private static class CachedCalendar {
        private final String version;
        private final HolidayCalendar calendar;

        private CachedCalendar(String version, HolidayCalendar calendar) {
            this.version = version;
            this.calendar = calendar;
        }
    }
}
