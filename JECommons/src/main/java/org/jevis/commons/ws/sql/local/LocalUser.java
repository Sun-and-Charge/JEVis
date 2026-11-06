/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.ws.sql.local;

import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisUser;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.sql.JEVisUserSQL;

/**
 * The logged-in user of a {@link LocalDataSource}. Read rights follow the data source ({@link LocalAccess}); the
 * other rights are answered by {@link org.jevis.commons.ws.sql.UserRightManagerForWS}. Answering a right does not
 * enable writing: the data source stays read-only.
 */
public class LocalUser implements JEVisUser {

    private final LocalDataSource ds;
    private final JEVisUserSQL user;

    LocalUser(LocalDataSource ds, JEVisUserSQL user) {
        this.ds = ds;
        this.user = user;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    @Override
    public String getFirstName() {
        return user.getFirstName();
    }

    @Override
    public String getLastName() {
        return user.getLastName();
    }

    @Override
    public boolean isSysAdmin() {
        return user.isSysAdmin();
    }

    @Override
    public long getUserID() {
        return user.getUserID();
    }

    @Override
    public JEVisObject getUserObject() {
        return ds.getObject(user.getUserID());
    }

    @Override
    public String getAccountName() {
        return user.getAccountName();
    }

    @Override
    public boolean canRead(long objectID) {
        return ds.canRead(objectID);
    }

    @Override
    public boolean canWrite(long objectID) {
        JsonObject obj = ds.getJson(objectID);
        return obj != null && ds.getUserManager().canWriteWOE(obj);
    }

    @Override
    public boolean canCreate(long objectID) {
        JsonObject obj = ds.getJson(objectID);
        if (obj == null) {
            return false;
        }
        try {
            return ds.getUserManager().canCreate(obj);
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean canCreate(long objectID, String jevisClass) {
        JsonObject obj = ds.getJson(objectID);
        return obj != null && ds.getUserManager().canCreateWOE(obj, jevisClass);
    }

    @Override
    public boolean canExecute(long objectID) {
        JsonObject obj = ds.getJson(objectID);
        return obj != null && ds.getUserManager().canExecuteWOE(obj);
    }

    @Override
    public boolean canDelete(long objectID) {
        JsonObject obj = ds.getJson(objectID);
        if (obj == null) {
            return false;
        }
        try {
            return ds.getUserManager().canDelete(obj);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * No-op: the user of one request does not change.
     */
    @Override
    public void reload() {
    }

    @Override
    public String toString() {
        return "LocalUser [" + user.getAccountName() + ":" + user.getUserID() + "]";
    }
}
