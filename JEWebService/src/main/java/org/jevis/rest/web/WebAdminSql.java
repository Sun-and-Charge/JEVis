/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.*;
import org.jevis.commons.ws.sql.CachedAccessControl;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.UserRightManagerForWS;
import org.jevis.commons.ws.sql.auth.AuthService;
import org.jevis.commons.ws.sql.auth.AuthStore;
import org.joda.time.DateTime;

import java.util.*;

/**
 * {@link WebAdmin.Env} on the {@link SQLDataSource} of a request. Objects and relationships are read once; the
 * changes of the request are applied to these lists too, so answers after a write show the new state (the
 * relationship cache of the data source is not updated by writes).
 */
final class WebAdminSql implements WebAdmin.Env {

    private static final Logger logger = LogManager.getLogger(WebAdminSql.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SQLDataSource ds;
    private final UserRightManagerForWS um;
    private Map<Long, JsonObject> objects;
    private Set<Long> readable;
    private Map<Long, Long> parents;
    private List<JsonRelationship> relationships;
    private final Map<Long, Map<String, String>> attributes = new HashMap<>();
    private AuthStore authStore;
    private Boolean authAvailable;

    WebAdminSql(SQLDataSource ds) {
        this.ds = ds;
        this.um = ds.getUserManager();
    }

    private void load() {
        if (objects != null) return;
        try {
            List<JsonObject> all = ds.getObjects();
            objects = new LinkedHashMap<>();
            for (JsonObject object : all) objects.put(object.getId(), object);
            readable = new HashSet<>();
            for (JsonObject object : um.filterList(all)) readable.add(object.getId());
            relationships = new ArrayList<>(ds.getRelationships());
            parents = new HashMap<>();
            for (JsonRelationship rel : relationships) {
                if (rel.getType() == JEVisConstants.ObjectRelationship.PARENT) parents.put(rel.getFrom(), rel.getTo());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the objects", e);
        }
    }

    @Override
    public long userId() {
        return ds.getCurrentUser().getUserID();
    }

    @Override
    public boolean sysAdmin() {
        return ds.getCurrentUser().isSysAdmin();
    }

    @Override
    public boolean interactive() {
        return ds.getAuthMethod() != SQLDataSource.AuthMethod.API_KEY;
    }

    @Override
    public Collection<JsonObject> objects() {
        load();
        return objects.values();
    }

    @Override
    public JsonObject object(long id) {
        load();
        return objects.get(id);
    }

    @Override
    public Long parent(long id) {
        load();
        return parents.get(id);
    }

    @Override
    public boolean canRead(JsonObject object) {
        load();
        return object != null && (um.isSysAdmin() || readable.contains(object.getId()));
    }

    @Override
    public boolean canWrite(JsonObject object) {
        return object != null && um.canWriteWOE(object);
    }

    @Override
    public boolean canCreate(JsonObject parent, String jevisClass) {
        return parent != null && um.canCreateWOE(parent, jevisClass);
    }

    @Override
    public boolean canDelete(JsonObject object) {
        try {
            return object != null && um.canDelete(object);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<JsonRelationship> relationships() {
        load();
        return relationships;
    }

    @Override
    public Map<String, String> userAttributes(long id) {
        Map<String, String> cached = attributes.get(id);
        if (cached != null) return cached;
        Map<String, String> values = new HashMap<>();
        for (JsonAttribute attribute : ds.getAttributes(id)) {
            if (!WebAdmin.USER_ATTRIBUTES.contains(attribute.getType())) continue;
            JsonSample latest = attribute.getLatestValue();
            if (latest != null && latest.getValue() != null) values.put(attribute.getType(), latest.getValue());
        }
        attributes.put(id, values);
        return values;
    }

    @Override
    public boolean hasMfa(long userId) {
        try {
            if (authStore == null) {
                authStore = new AuthStore(ds.getConnection());
                authAvailable = authStore.isSchemaAvailable();
            }
            return authAvailable && AuthService.hasMfa(authStore, userId);
        } catch (Exception e) {
            logger.debug("Could not read the two-factor status of {}", userId, e);
            return false;
        }
    }

    @Override
    public void setAttribute(JsonObject object, String attribute, int primitiveType, String value) throws Exception {
        JsonSample sample = WebEdit.sample(DateTime.now(), value, null);
        ds.setSamples(object.getId(), attribute, primitiveType, new ArrayList<>(Collections.singletonList(sample)));
        attributes.remove(object.getId());
        try {
            CachedAccessControl.getInstance(ds, true).checkForChanges(object, attribute, CachedAccessControl.Change.ADD);
        } catch (Exception e) {
            logger.error("Could not update the access control", e);
        }
    }

    @Override
    public JsonObject create(long parent, String jevisClass, String name, Map<String, String> localNames) throws Exception {
        JsonObject request = new JsonObject();
        request.setName(name);
        request.setJevisClass(jevisClass);
        request.setisPublic(false);
        String i18n = null;
        if (localNames != null && !localNames.isEmpty()) {
            List<JsonI18n> list = new ArrayList<>();
            for (Map.Entry<String, String> entry : localNames.entrySet()) {
                JsonI18n jsonI18n = new JsonI18n();
                jsonI18n.setKey(entry.getKey());
                jsonI18n.setValue(entry.getValue());
                list.add(jsonI18n);
            }
            i18n = MAPPER.writeValueAsString(list);
        }
        JsonObject created = ds.buildObject(request, parent, i18n);
        try {
            CachedAccessControl.getInstance(ds, true).checkForChanges(created, CachedAccessControl.Change.ADD);
        } catch (Exception e) {
            logger.error("Could not update the access control", e);
        }
        load();
        objects.put(created.getId(), created);
        readable.add(created.getId());
        parents.put(created.getId(), parent);
        JsonRelationship rel = new JsonRelationship();
        rel.setFrom(created.getId());
        rel.setTo(parent);
        rel.setType(JEVisConstants.ObjectRelationship.PARENT);
        relationships.add(rel);
        return created;
    }

    @Override
    public void rename(JsonObject object, String name) throws Exception {
        JsonObject current = ds.getObject(object.getId());
        String i18n = current.getI18n() != null && !current.getI18n().isEmpty() ? MAPPER.writeValueAsString(current.getI18n()) : null;
        ds.updateObject(object.getId(), name, current.getisPublic(), i18n);
    }

    @Override
    public void recycle(JsonObject object) throws Exception {
        ds.markAsDeletedObject(object);
        try {
            CachedAccessControl.getInstance(ds, true).invalidate();
        } catch (Exception e) {
            logger.error("Could not invalidate the access control", e);
        }
    }

    @Override
    public void deleteObject(JsonObject object) throws Exception {
        ds.deleteObject(object);
        load();
        objects.remove(object.getId());
    }

    @Override
    public void addRelationship(long from, long to, int type) throws Exception {
        JsonRelationship rel = new JsonRelationship();
        rel.setFrom(from);
        rel.setTo(to);
        rel.setType(type);
        JsonRelationship stored = ds.setRelationships(rel);
        try {
            CachedAccessControl.getInstance(ds, true).checkForChanges(stored != null ? stored : rel);
        } catch (Exception e) {
            logger.error("Could not update the access control", e);
        }
        load();
        relationships.add(rel);
    }

    @Override
    public boolean removeRelationship(long from, long to, int type) {
        boolean removed = ds.deleteRelationship(from, to, type);
        if (removed) {
            try {
                CachedAccessControl.getInstance(ds, true).checkForChanges(type);
            } catch (Exception e) {
                logger.error("Could not update the access control", e);
            }
            load();
            relationships.removeIf(rel -> rel.getFrom() == from && rel.getTo() == to && rel.getType() == type);
        }
        return removed;
    }

    @Override
    public void resetMfa(long userId) throws Exception {
        AuthService.disableMfa(ds, userId);
        AuthService.revokeAllForUser(ds, userId);
        logger.warn("Sys Admin '{}' reset the two-factor authentication of user {}", ds.getCurrentUser().getAccountName(), userId);
    }

    @Override
    public void log(SQLDataSource.LOG_EVENT event, String details) {
        ds.logUserAction(event, details);
    }
}
