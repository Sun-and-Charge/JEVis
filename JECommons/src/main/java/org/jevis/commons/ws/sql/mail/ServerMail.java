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
package org.jevis.commons.ws.sql.mail;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.classes.JC;
import org.jevis.commons.mail.MailRecipients;
import org.jevis.commons.mail.MailSettings;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonRelationship;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.JEVisClassHelper;
import org.jevis.commons.ws.sql.SQLDataSource;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Server side counterpart of {@link MailSettings#findDefault} and {@link MailRecipients} on the webservice
 * {@link SQLDataSource}. Reads the database directly, i.e. with system rights; callers check permissions.
 */
public final class ServerMail {

    public static final String USER_CLASS = "User";
    public static final String GROUP_CLASS = "Group";
    private static final Logger logger = LogManager.getLogger(ServerMail.class);
    private static final int[] MEMBERSHIPS = {
            JEVisConstants.ObjectRelationship.MEMBER_READ,
            JEVisConstants.ObjectRelationship.MEMBER_WRITE,
            JEVisConstants.ObjectRelationship.MEMBER_EXECUTE,
            JEVisConstants.ObjectRelationship.MEMBER_CREATE,
            JEVisConstants.ObjectRelationship.MEMBER_DELETE};

    private ServerMail() {
    }

    /**
     * @return the "EMail Plugin" marked as Default, or the only one; {@code null} if none is configured
     */
    public static MailSettings findDefaultSettings(SQLDataSource ds) {
        try {
            List<Long> plugins = objectIds(ds, MailSettings.CLASS_NAME);
            for (Long plugin : plugins) {
                if (Boolean.parseBoolean(value(ds, plugin, JC.JENotifierPlugin.EMailPlugin.a_Default))) {
                    return settingsOf(ds, plugin);
                }
            }
            if (plugins.size() == 1) {
                return settingsOf(ds, plugins.get(0));
            }
            if (plugins.size() > 1) {
                logger.warn("{} EMail Plugin objects found but none is marked as Default", plugins.size());
            }
        } catch (Exception e) {
            logger.error("Could not read the default mail settings", e);
        }
        return null;
    }

    /**
     * Ids of the not deleted objects of a class and its heirs, read directly from the database (the object cache
     * of {@link SQLDataSource} may be incomplete).
     */
    static List<Long> objectIds(SQLDataSource ds, String jevisClass) throws SQLException {
        List<String> classes = new ArrayList<>();
        classes.add(jevisClass);
        JEVisClassHelper.findHeir(jevisClass, classes);
        StringBuilder sql = new StringBuilder("SELECT id FROM object WHERE deletets IS NULL AND type IN (");
        for (int i = 0; i < classes.size(); i++) {
            sql.append(i == 0 ? "?" : ",?");
        }
        sql.append(") ORDER BY id");
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = ds.getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < classes.size(); i++) {
                ps.setString(i + 1, classes.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    static MailSettings settingsOf(SQLDataSource ds, long pluginId) {
        String host = value(ds, pluginId, JC.JENotifierPlugin.EMailPlugin.a_SMTPServer);
        int port = MailSettings.parsePort(value(ds, pluginId, JC.JENotifierPlugin.EMailPlugin.a_Port));
        String user = value(ds, pluginId, JC.JENotifierPlugin.EMailPlugin.a_ServerUserName);
        String password = value(ds, pluginId, JC.JENotifierPlugin.EMailPlugin.a_Password);
        MailSettings.Security security = MailSettings.parseSecurity(value(ds, pluginId, JC.JENotifierPlugin.EMailPlugin.a_TransportSecurity));
        String authenticator = value(ds, pluginId, JC.JENotifierPlugin.EMailPlugin.a_Authenticator);
        boolean auth = authenticator == null || !authenticator.trim().equalsIgnoreCase("NO");
        return new MailSettings(host, port, user, password, security, auth);
    }

    /**
     * @return the latest value of an attribute, or {@code null}
     */
    public static String value(SQLDataSource ds, long objectId, String attribute) {
        try {
            JsonSample sample = ds.getLastSample(objectId, attribute);
            return sample != null ? sample.getValue() : null;
        } catch (Exception e) {
            logger.warn("Could not read {} of {}", attribute, objectId, e);
            return null;
        }
    }

    /**
     * @return {@code false} if the user's "Enabled" attribute is false
     */
    public static boolean isEnabled(SQLDataSource ds, long userId) {
        String enabled = value(ds, userId, JC.User.a_Enabled);
        return enabled == null || !(enabled.trim().equalsIgnoreCase("false") || enabled.trim().equals("0"));
    }

    /**
     * @return the e-mail addresses of a user (empty if disabled or none)
     */
    public static List<String> addressesOfUser(SQLDataSource ds, long userId) {
        if (!isEnabled(ds, userId)) return Collections.emptyList();
        return MailRecipients.parseAddresses(value(ds, userId, JC.User.a_EMail));
    }

    /**
     * @return the ids of the users that are members of the group
     */
    public static Set<Long> membersOf(SQLDataSource ds, long groupId) {
        Set<Long> members = new LinkedHashSet<>();
        for (int type : MEMBERSHIPS) {
            for (JsonRelationship relationship : ds.getRelationships(type)) {
                if (relationship.getTo() == groupId) {
                    members.add(relationship.getFrom());
                }
            }
        }
        return members;
    }

    /**
     * Resolves User and Group objects to addresses; objects of other classes are ignored.
     *
     * @param objects User and Group objects
     */
    public static Set<String> resolve(SQLDataSource ds, Collection<JsonObject> objects) {
        Set<String> addresses = new LinkedHashSet<>();
        for (JsonObject object : objects) {
            if (object == null) continue;
            if (USER_CLASS.equals(object.getJevisClass())) {
                addresses.addAll(addressesOfUser(ds, object.getId()));
            } else if (GROUP_CLASS.equals(object.getJevisClass())) {
                for (Long member : membersOf(ds, object.getId())) {
                    try {
                        JsonObject user = ds.getObject(member);
                        if (user != null && USER_CLASS.equals(user.getJevisClass())) {
                            addresses.addAll(addressesOfUser(ds, member));
                        }
                    } catch (Exception e) {
                        logger.warn("Could not load group member {}", member, e);
                    }
                }
            } else {
                logger.warn("{} is neither a user nor a group, ignored as mail recipient", object.getId());
            }
        }
        return addresses;
    }
}
