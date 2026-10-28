/**
 * Copyright (C) JEVis contributors
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

import org.jevis.commons.ws.json.JsonObject;

/**
 * @author fs
 */
public class JEVisUserSQL {

    private final boolean isSysAdmin;
    private final long uID;
    private final boolean enabled;
    private String lastName;
    private final String firstname;
    private JsonObject userObj;
    private final String accountName;
    private SQLDataSource ds;
    private String password;
    private String entraID;

    public JEVisUserSQL(SQLDataSource ds, String account, Long objID, boolean isSysAdmin, boolean enabled, String password, String entraID) {
        this(ds, account, objID, isSysAdmin, enabled);
        this.password = password;
        this.entraID = entraID;
    }

    public JEVisUserSQL(SQLDataSource ds, String account, Long objID, boolean isSysAdmin, boolean enabled) {
        this.isSysAdmin = isSysAdmin;
        this.uID = objID;
        this.userObj = null;
        this.firstname = "";
        this.lastName = "";
        this.enabled = enabled;
        this.accountName = account;
        this.ds = ds;
    }

    public void setDataSource(SQLDataSource dataSource) {
        this.ds = dataSource;
    }

    public String getAccountName() {
        return accountName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getFirstName() {
        return firstname;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String name) {
        lastName = name;
    }

    public boolean isSysAdmin() {
        return isSysAdmin;
    }

    public long getUserID() {
        return uID;
    }

    public String getPassword() {
        return password;
    }

    @Deprecated
    public JsonObject getUserObject() {
        JsonObject userObj = new JsonObject();
        userObj.setName(accountName);
        userObj.setId(getUserID());
        userObj.setJevisClass("User");
        return userObj;

        /*
        if (userObj == null) {
            try {
                userObj = ds.getObject(uID);
            } catch (JEVisException ex) {
                ex.printStackTrace();
            }
        }

        return userObj;

         */
    }


    public String getEntraID() {
        return entraID;
    }

    public void setEntraID(String entraID) {
        this.entraID = entraID;
    }


    @Override
    public String toString() {
        return "JEVisUserNew{" +
                "isSysAdmin=" + isSysAdmin +
                ", uID=" + uID +
                ", enabled=" + enabled +
                ", lastName='" + lastName + '\'' +
                ", firstname='" + firstname + '\'' +
                ", userObj=" + userObj +
                ", accountName='" + accountName + '\'' +
                ", entraID='" + entraID + '\'' +
                '}';
    }
}
