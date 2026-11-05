/**
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
package org.jevis.commons.object;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.i18n.I18n;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Display format of the values of a data row (upstream issue #1194).
 * <p>
 * The number of decimal places is read from the optional "Decimal Places" attribute of the data object. If it is empty,
 * it is taken from the parent data object (e.g. the Data of a Clean Data), then from the measurement instrument whose
 * "Online ID" points to the data object, then from a measurement instrument above it in the tree (attribute "Decimal
 * Places" of the measurement instrument) and finally {@link #DEFAULT_DECIMALS} is used. The same fallback chain is used
 * for the medium, see {@link DataMedium}.
 * <p>
 * Values are shown with exactly this number of decimal places and with the grouping (thousands separator) of the
 * user's locale (rounded half up). Explicit settings of a chart or a dashboard widget win over the decimal places of the data row.
 */
public final class ValueFormat {

    public static final String ATTRIBUTE_NAME = "Decimal Places";
    public static final int DEFAULT_DECIMALS = 2;
    public static final int MAX_DECIMALS = 15;
    /**
     * How long the decimal places of the measurement instruments are cached per data source
     */
    static final long CACHE_MILLIS = 5 * 60 * 1000L;
    private static final Logger logger = LogManager.getLogger(ValueFormat.class);
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");
    private static final Map<JEVisDataSource, CachedMeters> METER_CACHE = new WeakHashMap<>();

    private ValueFormat() {
    }

    /**
     * Parses the value of a "Decimal Places" attribute leniently, e.g. "3", " 3.0 " or "3 digits". On the measurement
     * instrument it is a text attribute.
     *
     * @return the decimal places (at most {@link #MAX_DECIMALS}), or null if the value is empty, negative or no number
     */
    public static Integer parse(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = NUMBER.matcher(value.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            long decimals = Math.round(Double.parseDouble(matcher.group().replace(',', '.')));
            if (decimals < 0) {
                return null;
            }
            return (int) Math.min(decimals, MAX_DECIMALS);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Resolves the decimal places of a data object, see the class description.
     *
     * @return the decimal places, {@link #DEFAULT_DECIMALS} if none is configured
     */
    public static int decimals(JEVisObject dataObject) {
        return decimals(dataObject, null);
    }

    /**
     * Same as {@link #decimals(JEVisObject)}, with a map of data object id to the decimal places of the measurement
     * instrument whose "Online ID" points to it, see {@link #meterDecimalsByOnlineId(JEVisDataSource)}. If the map is
     * null, the cached map of the data source is used.
     */
    public static int decimals(JEVisObject dataObject, Map<Long, Integer> meterDecimalsByOnlineId) {
        Integer decimals = configuredDecimals(dataObject, meterDecimalsByOnlineId);
        return decimals != null ? decimals : DEFAULT_DECIMALS;
    }

    /**
     * @return the configured decimal places of a data object, or null if neither the data object nor a measurement
     * instrument defines them
     */
    public static Integer configuredDecimals(JEVisObject dataObject, Map<Long, Integer> meterDecimalsByOnlineId) {
        if (dataObject == null) {
            return null;
        }
        try {
            /* the meters are only read when a data object has no decimal places of its own */
            AtomicReference<Map<Long, Integer>> meters = new AtomicReference<>(meterDecimalsByOnlineId);
            return DataObjectChain.resolve(dataObject,
                    ValueFormat::fromAttribute,
                    object -> {
                        if (meters.get() == null) {
                            meters.set(cachedMeterDecimals(object.getDataSource()));
                        }
                        return meters.get().get(object.getID());
                    },
                    parent -> isMeter(parent) ? fromAttribute(parent) : null);
        } catch (Exception e) {
            logger.warn("Could not determine decimal places of object {}", dataObject, e);
        }
        return null;
    }

    /**
     * @return map of the data object id an "Online ID" points to, to the decimal places of the measurement instrument;
     * meters without decimal places are left out
     */
    public static Map<Long, Integer> meterDecimalsByOnlineId(JEVisDataSource ds) {
        Map<Long, Integer> result = new HashMap<>();
        for (Map.Entry<Long, JEVisObject> entry : DataObjectChain.metersByOnlineId(ds).entrySet()) {
            try {
                Integer decimals = fromAttribute(entry.getValue());
                if (decimals != null) {
                    result.put(entry.getKey(), decimals);
                }
            } catch (Exception e) {
                logger.warn("Could not read decimal places of measurement instrument {}", entry.getValue(), e);
            }
        }
        return result;
    }

    /**
     * Forgets the cached decimal places of the measurement instruments of a data source, e.g. after they were changed.
     */
    public static void invalidateCache(JEVisDataSource ds) {
        synchronized (METER_CACHE) {
            METER_CACHE.remove(ds);
        }
    }

    static Map<Long, Integer> cachedMeterDecimals(JEVisDataSource ds) {
        if (ds == null) {
            return new HashMap<>();
        }
        synchronized (METER_CACHE) {
            CachedMeters cached = METER_CACHE.get(ds);
            if (cached != null && System.currentTimeMillis() - cached.created < CACHE_MILLIS) {
                return cached.decimals;
            }
        }
        Map<Long, Integer> decimals = meterDecimalsByOnlineId(ds);
        synchronized (METER_CACHE) {
            METER_CACHE.put(ds, new CachedMeters(decimals));
        }
        return decimals;
    }

    /**
     * @return a format with exactly the given decimal places and grouping for the locale
     */
    public static NumberFormat numberFormat(int decimals, Locale locale) {
        int fractionDigits = Math.max(0, Math.min(decimals, MAX_DECIMALS));
        NumberFormat nf = NumberFormat.getNumberInstance(locale != null ? locale : Locale.getDefault());
        nf.setGroupingUsed(true);
        nf.setMinimumFractionDigits(fractionDigits);
        nf.setMaximumFractionDigits(fractionDigits);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        return nf;
    }

    /**
     * @return a format with exactly the given decimal places for the locale of the user
     */
    public static NumberFormat numberFormat(int decimals) {
        return numberFormat(decimals, userLocale());
    }

    /**
     * @return the format of the values of a data object for the locale of the user
     */
    public static NumberFormat numberFormat(JEVisObject dataObject) {
        return numberFormat(decimals(dataObject), userLocale());
    }

    /**
     * @return the format of the values of a data object for a locale
     */
    public static NumberFormat numberFormat(JEVisObject dataObject, Locale locale) {
        return numberFormat(decimals(dataObject), locale);
    }

    private static Locale userLocale() {
        try {
            Locale locale = I18n.getInstance().getLocale();
            if (locale != null) {
                return locale;
            }
        } catch (Exception e) {
            logger.debug("No user locale", e);
        }
        return Locale.getDefault();
    }

    private static Integer fromAttribute(JEVisObject object) throws Exception {
        JEVisAttribute attribute = object.getAttribute(ATTRIBUTE_NAME);
        if (attribute != null && attribute.hasSample()) {
            JEVisSample sample = attribute.getLatestSample();
            return sample != null ? parse(sample.getValueAsString()) : null;
        }
        return null;
    }

    private static boolean isMeter(JEVisObject object) throws Exception {
        String className = object.getJEVisClassName();
        return className != null && className.contains(DataObjectChain.MEASUREMENT_INSTRUMENT);
    }

    private static final class CachedMeters {
        private final long created = System.currentTimeMillis();
        private final Map<Long, Integer> decimals;

        private CachedMeters(Map<Long, Integer> decimals) {
            this.decimals = decimals;
        }
    }
}
