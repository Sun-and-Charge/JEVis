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

/**
 * Read-only implementation of the JEVis API ({@link org.jevis.api.JEVisDataSource} and friends) on top of the
 * per-request {@link org.jevis.commons.ws.sql.SQLDataSource} of the web service, for the logged-in user.
 * <p>
 * Usage in a web service resource:
 * <pre>{@code
 * SQLDataSource sql = null;
 * try {
 *     sql = new SQLDataSource(httpHeaders::getRequestHeader);
 *     LocalDataSource ds = new LocalDataSource(sql);
 *     JEVisObject obj = ds.getObject(id);          // null if the user may not read it
 *     ...
 * } finally {
 *     Config.CloseDS(sql);
 * }
 * }</pre>
 * See {@link org.jevis.commons.ws.sql.local.LocalDataSource} for the semantics, the read-only contract and the
 * caching.
 *
 * <h2>Verification against a database</h2>
 * The unit tests cover rights, tree, classes, attributes and the sample code paths with a mocked
 * {@code SQLDataSource}. With a demo database, check for a normal user and a sys admin (same login in the desktop
 * client or with {@code curl} against {@code /JEWebService/v1}):
 * <ol>
 *     <li>{@code getObjects()} has the same IDs as {@code GET /objects?rel=false} (without recycle bin),
 *     {@code getDeletedObjects()} the same as {@code GET /objects?onlyDeleted=true}.</li>
 *     <li>{@code getObject(id)} is {@code null} exactly where {@code GET /objects/{id}} answers 404, e.g. an object
 *     owned only by a group the user is not a member of.</li>
 *     <li>{@code getRelationships()} has the same entries as {@code GET /relationships}; {@code getChildren()} of a
 *     building or directory matches the tree of the Control Center.</li>
 *     <li>{@code getAttributes(id)} matches {@code GET /objects/{id}/attributes} (units, sample rates, begins/ends,
 *     sample count, latest value).</li>
 *     <li>For a Data and a Clean Data object: {@code getSamples(from, until)} equals
 *     {@code GET .../samples?from&until} (compare timestamps and values), and
 *     {@code getSamples(from, until, cwd, ap, mm, tz)} equals {@code GET .../samples?from&until&cwd&ap&mm&tz} for
 *     e.g. {@code ap=DAILY,mm=NONE}, {@code ap=MONTHLY,mm=TOTAL}, {@code ap=NONE,mm=AVERAGE}, with {@code cwd=true}
 *     on an object below a building with a custom work day, and {@code tz=Europe/Berlin} over a DST change.</li>
 *     <li>A File attribute: {@code getLatestSample().getValueAsFile()} returns the same bytes as
 *     {@code GET .../samples/files/latest}.</li>
 *     <li>Run a calculation with {@code CalcJobFactory}/{@code CalcJob.getResults()} on this data source and compare
 *     with the result of the JECalc service; the database must be unchanged afterwards (sample counts and
 *     {@code maxts} of the output attribute).</li>
 * </ol>
 */
package org.jevis.commons.ws.sql.local;
