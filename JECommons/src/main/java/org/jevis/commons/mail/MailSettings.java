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
package org.jevis.commons.mail;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.classes.JC;

import java.util.List;

/**
 * SMTP settings of an "EMail Plugin" object.
 * <p>
 * The system-wide mail setting is the "EMail Plugin" marked as "Default"; if there is only one EMail Plugin it is used
 * even without the flag. Services that reference their own EMail Plugin ("Notification ID") keep using that one.
 */
public class MailSettings {

    public static final String CLASS_NAME = JC.JENotifierPlugin.EMailPlugin.name;
    private static final Logger logger = LogManager.getLogger(MailSettings.class);

    public enum Security {NONE, STARTTLS, SSL}

    private String host;
    private int port = 25;
    private String user;
    private String password;
    private Security security = Security.STARTTLS;
    private boolean authentication = true;
    private String from;

    public MailSettings() {
    }

    public MailSettings(String host, int port, String user, String password, Security security, boolean authentication) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.security = security;
        this.authentication = authentication;
    }

    /**
     * Reads the settings of an "EMail Plugin" object.
     */
    public static MailSettings of(JEVisObject emailPlugin) {
        MailSettings settings = new MailSettings();
        settings.host = value(emailPlugin, JC.JENotifierPlugin.EMailPlugin.a_SMTPServer);
        String port = value(emailPlugin, JC.JENotifierPlugin.EMailPlugin.a_Port);
        settings.port = parsePort(port);
        settings.user = value(emailPlugin, JC.JENotifierPlugin.EMailPlugin.a_ServerUserName);
        settings.password = value(emailPlugin, JC.JENotifierPlugin.EMailPlugin.a_Password);
        settings.security = parseSecurity(value(emailPlugin, JC.JENotifierPlugin.EMailPlugin.a_TransportSecurity));
        String authenticator = value(emailPlugin, JC.JENotifierPlugin.EMailPlugin.a_Authenticator);
        settings.authentication = authenticator == null || !authenticator.trim().equalsIgnoreCase("NO");
        return settings;
    }

    /**
     * @return the system-wide mail settings, or null if no "EMail Plugin" is configured
     */
    public static MailSettings findDefault(JEVisDataSource ds) {
        try {
            JEVisClass pluginClass = ds.getJEVisClass(CLASS_NAME);
            if (pluginClass == null) {
                return null;
            }
            List<JEVisObject> plugins = ds.getObjects(pluginClass, true);
            for (JEVisObject plugin : plugins) {
                if (Boolean.parseBoolean(value(plugin, JC.JENotifierPlugin.EMailPlugin.a_Default))) {
                    return of(plugin);
                }
            }
            if (plugins.size() == 1) {
                return of(plugins.get(0));
            }
        } catch (Exception e) {
            logger.error("Could not read the default mail settings", e);
        }
        return null;
    }

    public static int parsePort(String port) {
        try {
            return port != null && !port.trim().isEmpty() ? (int) Double.parseDouble(port.trim()) : 25;
        } catch (NumberFormatException e) {
            return 25;
        }
    }

    /**
     * Same values as the existing E-Mail notifier: "&lt;&lt;No&gt;&gt;", "STARTTLS", "SSL"; default STARTTLS.
     */
    public static Security parseSecurity(String value) {
        if (value == null) {
            return Security.STARTTLS;
        }
        String v = value.trim();
        if (v.equalsIgnoreCase("SSL")) {
            return Security.SSL;
        } else if (v.equalsIgnoreCase("<<No>>") || v.equalsIgnoreCase("NO") || v.equalsIgnoreCase("NONE")) {
            return Security.NONE;
        }
        return Security.STARTTLS;
    }

    private static String value(JEVisObject object, String attributeName) {
        try {
            JEVisAttribute attribute = object.getAttribute(attributeName);
            if (attribute != null && attribute.hasSample()) {
                JEVisSample sample = attribute.getLatestSample();
                return sample != null ? sample.getValueAsString() : null;
            }
        } catch (Exception e) {
            logger.warn("Could not read {} of {}", attributeName, object, e);
        }
        return null;
    }

    public boolean isValid() {
        return host != null && !host.trim().isEmpty();
    }

    /**
     * @return the sender address: the explicit sender, else the server user name
     */
    public String getFrom() {
        return from != null && !from.isEmpty() ? from : user;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public Security getSecurity() {
        return security;
    }

    public boolean isAuthentication() {
        return authentication;
    }

    @Override
    public String toString() {
        return "MailSettings{" + host + ":" + port + ", user=" + user + ", security=" + security + ", auth=" + authentication + '}';
    }
}
