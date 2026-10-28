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
package org.jevis.commons.ws.sql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jevis.commons.ws.json.JsonObject;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.UUID;

@XmlRootElement(name = "Session")
public class Session {

    private boolean isEntraID = false;
    private JsonObject user;
    private String displayName = "-";
    private String id = UUID.randomUUID().toString();
    private String entraToken;
    private JEVisUserSQL jevisUser;

    public Session() {
    }

    public Session(boolean isEntraID, JsonObject user, String displayName, String entraToken) {
        this.isEntraID = isEntraID;
        this.user = user;
        this.displayName = displayName;
        this.entraToken = entraToken;

    }

    @XmlElement(name = "id")
    public String getId() {
        return id;
    }

    @XmlElement(name = "name")
    public String getDisplayName() {
        return displayName;
    }

    @XmlElement(name = "user")
    public JsonObject getUser() {
        return user;
    }

    public void setUser(JsonObject user) {
        this.user = user;
    }

    @JsonIgnore
    public JEVisUserSQL getJevisUser() {
        return jevisUser;
    }

    public void setJevisUser(JEVisUserSQL jevisUser) {
        this.jevisUser = jevisUser;
    }

    /**
     * The Entra access token is only needed server side and must never be serialized.
     */
    @JsonIgnore
    public String getEntraToken() {
        return entraToken;
    }

    @Override
    public String toString() {
        return "Session{" +
                "isEntraID=" + isEntraID +
                ", displayName='" + displayName + '\'' +
                '}';
    }
}
