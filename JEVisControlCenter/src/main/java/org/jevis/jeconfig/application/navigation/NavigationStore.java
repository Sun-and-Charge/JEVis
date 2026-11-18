/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEConfig.
 * <p>
 * JEConfig is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEConfig is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEConfig. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEConfig is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.application.navigation;

import javafx.beans.property.SimpleLongProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Favourites, recently opened analyses/dashboards and the selected site of the current user on the current server
 * (J25.5 stage 3). Kept in the user preferences of this computer, as the web app keeps them in the browser.
 */
public final class NavigationStore {
    private static final Logger logger = LogManager.getLogger(NavigationStore.class);
    private static final int MAX_RECENT = 10;
    private static final SimpleLongProperty SITE = new SimpleLongProperty(0);
    private static Preferences node;

    private NavigationStore() {
    }

    /** binds the store to the logged-in user and server */
    public static void init(JEVisDataSource ds, String server) {
        try {
            String account = (server == null ? "" : server) + "|" + ds.getCurrentUser().getUserID();
            node = Preferences.userRoot().node("JEVis.JEConfig.Navigation").node(Integer.toHexString(account.hashCode()));
            SITE.set(node.getLong("site", 0));
            SITE.addListener((observable, oldValue, newValue) -> node.putLong("site", newValue.longValue()));
        } catch (Exception e) {
            logger.warn("Navigation preferences not available", e);
        }
    }

    /** id of the selected site (Building), 0 = all sites */
    public static SimpleLongProperty siteProperty() {
        return SITE;
    }

    public static List<Long> favourites() {
        return ids("favourites");
    }

    public static List<Long> recent() {
        return ids("recent");
    }

    public static boolean isFavourite(JEVisObject object) {
        return object != null && favourites().contains(object.getID());
    }

    public static void toggleFavourite(JEVisObject object) {
        if (object == null) return;
        List<Long> favourites = favourites();
        if (!favourites.remove(object.getID())) favourites.add(object.getID());
        store("favourites", favourites);
    }

    /** remembers an opened analysis or dashboard, newest first */
    public static void recordOpened(JEVisObject object) {
        if (object == null || node == null) return;
        List<Long> recent = recent();
        recent.remove(object.getID());
        recent.add(0, object.getID());
        while (recent.size() > MAX_RECENT) recent.remove(recent.size() - 1);
        store("recent", recent);
    }

    private static List<Long> ids(String key) {
        List<Long> ids = new ArrayList<>();
        if (node == null) return ids;
        for (String part : node.get(key, "").split(",")) {
            try {
                if (!part.trim().isEmpty()) ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private static void store(String key, List<Long> ids) {
        if (node == null) return;
        StringBuilder value = new StringBuilder();
        for (Long id : ids) {
            if (value.length() > 0) value.append(',');
            value.append(id);
        }
        node.put(key, value.toString());
    }
}
