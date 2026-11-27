/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.ws.sql;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** E10.0: actions of Sys Admins logged in as people are audited; their service logins are not. */
public class AdminAuditTest {

    @Test
    public void sysAdminsAreLoggedWhenTheyWorkInteractively() {
        assertTrue(SQLDataSource.logsActionsOf(true, SQLDataSource.AuthMethod.ACCESS_TOKEN));
        assertTrue(SQLDataSource.logsActionsOf(true, SQLDataSource.AuthMethod.SESSION));
        assertFalse(SQLDataSource.logsActionsOf(true, SQLDataSource.AuthMethod.API_KEY));
        assertFalse(SQLDataSource.logsActionsOf(true, SQLDataSource.AuthMethod.BASIC));
    }

    @Test
    public void otherUsersAreAlwaysLogged() {
        for (SQLDataSource.AuthMethod method : SQLDataSource.AuthMethod.values()) {
            assertTrue(SQLDataSource.logsActionsOf(false, method));
        }
        assertTrue(SQLDataSource.logsActionsOf(false, null));
    }
}
