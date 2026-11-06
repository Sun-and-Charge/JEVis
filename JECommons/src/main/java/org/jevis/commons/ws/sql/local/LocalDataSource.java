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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.JEVisFileImp;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.jevis.commons.unit.JEVisUnitImp;
import org.jevis.commons.ws.json.*;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.UserRightManagerForWS;
import org.jevis.commons.ws.sql.sg.JsonSampleGenerator;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import tech.units.indriya.AbstractUnit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only {@link JEVisDataSource} on top of the per-request {@link SQLDataSource} of the web service, so that
 * JECommons logic written against the JEVis API (calculations, clean data, work days, analyses) can run inside the
 * web service for the logged-in user, without HTTP.
 *
 * <h2>Semantics</h2>
 * The read methods behave like the REST client {@code JEVisDataSourceWS} talking to {@code JEWebService}:
 * <ul>
 *     <li>Rights: an object the user may not read does not exist for this data source ({@link #getObject(Long)}
 *     returns {@code null}, it is missing in children, parents, relationships and object lists). The rule is the one of
 *     {@link UserRightManagerForWS}, see {@link LocalAccess}.</li>
 *     <li>{@link #getObjects()} returns the readable objects which are not in the recycle bin;
 *     {@link #getObject(Long)} also returns readable objects of the recycle bin (with a delete timestamp), like the
 *     client.</li>
 *     <li>Samples are read with the same code as {@code GET .../samples}; with aggregation or manipulation the
 *     {@link JsonSampleGenerator} is called directly.</li>
 *     <li>Classes come from the class definition files ({@link SQLDataSource#getJEVisClasses()}, i.e.
 *     {@link Config#getClassCache()}), the same source as {@code GET /classes}.</li>
 * </ul>
 *
 * <h2>Read-only</h2>
 * Every method that would persist something ({@code build*}, {@code commit}, {@code delete*}, {@code addSamples},
 * setters of objects, attributes, classes and types) throws {@link UnsupportedOperationException}. The only exception
 * are {@link JEVisSample#setValue(Object)}, {@link JEVisSample#setValue(Object, JEVisUnit)} and
 * {@link JEVisSample#setNote(String)} of samples returned by this data source: they change the in-memory sample only
 * (e.g. to replace limit violations in an analysis) and are never written to the database.
 * {@code reload*}, {@link #preload()} and {@link #clearCache()} only touch the in-memory caches.
 *
 * <h2>Lifetime and caching</h2>
 * One instance lives for one web request, like the {@link SQLDataSource} it wraps; it is not shared between threads
 * of different requests. Objects, relationships and classes are loaded once (all objects and all relationships in
 * one query each, through the request cache of {@link SQLDataSource}) and indexed, so walking large trees does not
 * cause a query per object. Attributes are loaded per object on first use, or for all objects with
 * {@link #getAttributes()}. Samples are never cached. The {@link SQLDataSource} stays owned by the caller, who also
 * closes it; {@link #disconnect()} does not close it.
 */
public class LocalDataSource implements JEVisDataSource {

    /**
     * Default {@code limit} of {@code GET .../samples}, used when aggregation and manipulation are both empty or
     * {@code NONE}.
     */
    static final long REST_DEFAULT_SAMPLE_LIMIT = 1000000L;
    private static final Logger logger = LogManager.getLogger(LocalDataSource.class);
    private static final String MSG_READ_ONLY = " is not supported: the server-side data source is read-only";

    private final SQLDataSource sql;
    private final UserRightManagerForWS um;
    private final long userID;
    private final JEVisInfo info = new JEVisInfo() {
        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public String getName() {
            return "JECommons-Local";
        }
    };
    private final Map<Long, LocalObject> objectCache = new ConcurrentHashMap<>();
    private final Map<Long, List<JEVisAttribute>> attributeCache = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> iconCache = new ConcurrentHashMap<>();
    private List<JEVisOption> config = new ArrayList<>();
    private volatile String language;
    private Map<Long, JsonObject> jsonObjects;
    private LocalAccess access;
    private List<JEVisRelationship> relationships;
    private Map<Long, List<JEVisRelationship>> relationshipsByObject;
    private Map<String, LocalClass> classes;
    private boolean allAttributesLoaded = false;
    private LocalUser user;

    /**
     * @param sql the authenticated data source of the current request; it must have a user and a rights manager
     *            (i.e. created with login)
     * @throws IllegalArgumentException if the data source is not authenticated
     */
    public LocalDataSource(SQLDataSource sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQLDataSource is null");
        }
        if (sql.getUserManager() == null) {
            throw new IllegalArgumentException("SQLDataSource is not authenticated (no user rights manager)");
        }
        this.sql = sql;
        this.um = sql.getUserManager();
        this.userID = sql.getCurrentUser().getUserID();
    }

    static UnsupportedOperationException readOnly(String operation) {
        return new UnsupportedOperationException(operation + MSG_READ_ONLY);
    }

    /**
     * Converts a timestamp the way the REST client sends it: formatted as {@code yyyyMMdd'T'HHmmss} in UTC, i.e.
     * milliseconds are dropped and the result is in UTC.
     */
    static DateTime toRestTimestamp(DateTime ts) {
        if (ts == null) {
            return null;
        }
        return ts.withZone(DateTimeZone.UTC).withMillisOfSecond(0);
    }

    static boolean isPasswordAttribute(JsonObject obj, int primitiveType, String attribute) {
        return primitiveType == JEVisConstants.PrimitiveType.PASSWORD_PBKDF2
                || ("User".equals(obj.getJevisClass()) && "Password".equals(attribute));
    }

    /**
     * @return the wrapped request data source
     */
    public SQLDataSource getSQLDataSource() {
        return sql;
    }

    /**
     * @return the language used by {@link JEVisObject#getName()}, {@code null} for the untranslated name
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Sets the language (ISO 639 code, e.g. {@code de}) used by {@link JEVisObject#getName()}. The desktop client
     * uses the locale of its UI; on the server there is no global locale, so the default ({@code null}) is the
     * untranslated object name.
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    // ---------------------------------------------------------------- lazy request caches

    synchronized Map<Long, JsonObject> jsonObjects() {
        if (jsonObjects == null) {
            Map<Long, JsonObject> map = new LinkedHashMap<>();
            try {
                for (JsonObject json : sql.getObjects()) {
                    map.put(json.getId(), json);
                }
            } catch (JEVisException ex) {
                throw new IllegalStateException("Could not load objects", ex);
            }
            jsonObjects = map;
        }
        return jsonObjects;
    }

    synchronized LocalAccess access() {
        if (access == null) {
            List<JsonRelationship> all = um.isSysAdmin() ? Collections.<JsonRelationship>emptyList() : sql.getRelationships();
            access = LocalAccess.of(um, userID, all);
        }
        return access;
    }

    private synchronized void loadRelationships() {
        if (relationships != null) {
            return;
        }
        Map<Long, JsonObject> objects = jsonObjects();
        LocalAccess rights = access();
        List<JEVisRelationship> list = new ArrayList<>();
        Map<Long, List<JEVisRelationship>> byObject = new HashMap<>();
        for (JsonRelationship json : sql.getRelationships()) {
            if (!rights.canRead(json, objects)) {
                continue;
            }
            LocalRelationship rel = new LocalRelationship(this, json);
            list.add(rel);
            addToIndex(byObject, json.getFrom(), rel);
            if (json.getTo() != json.getFrom()) {
                addToIndex(byObject, json.getTo(), rel);
            }
        }
        relationshipsByObject = byObject;
        relationships = list;
    }

    private static void addToIndex(Map<Long, List<JEVisRelationship>> index, long id, JEVisRelationship rel) {
        List<JEVisRelationship> list = index.get(id);
        if (list == null) {
            list = new ArrayList<>();
            index.put(id, list);
        }
        list.add(rel);
    }

    private synchronized Map<String, LocalClass> classes() {
        if (classes == null) {
            Map<String, LocalClass> map = new LinkedHashMap<>();
            List<JsonJEVisClass> jsons = sql.getJEVisClasses();
            if (jsons != null) {
                for (JsonJEVisClass json : jsons) {
                    if (json != null && json.getName() != null) {
                        map.put(json.getName(), new LocalClass(this, json));
                    }
                }
            }
            classes = map;
        }
        return classes;
    }

    /**
     * @return {@code true} if the current user may read the object with the given ID
     */
    boolean canRead(long id) {
        return access().canRead(jsonObjects().get(id));
    }

    JsonObject getJson(long id) {
        return jsonObjects().get(id);
    }

    long getUserID() {
        return userID;
    }

    UserRightManagerForWS getUserManager() {
        return um;
    }

    // ---------------------------------------------------------------- configuration

    @Override
    public void init(List<JEVisOption> config) throws IllegalArgumentException {
        setConfiguration(config);
    }

    @Override
    public List<JEVisOption> getConfiguration() {
        return config;
    }

    @Override
    public void setConfiguration(List<JEVisOption> config) {
        this.config = config != null ? config : new ArrayList<JEVisOption>();
    }

    // ---------------------------------------------------------------- objects

    @Override
    public JEVisObject getObject(Long id) {
        if (id == null) {
            return null;
        }
        LocalObject cached = objectCache.get(id);
        if (cached != null) {
            return cached;
        }
        JsonObject json = jsonObjects().get(id);
        if (json == null || !access().canRead(json)) {
            return null;
        }
        LocalObject obj = new LocalObject(this, json);
        LocalObject existing = objectCache.putIfAbsent(id, obj);
        return existing != null ? existing : obj;
    }

    /**
     * @return the readable objects which are not in the recycle bin
     */
    @Override
    public List<JEVisObject> getObjects() {
        List<JEVisObject> list = new ArrayList<>();
        for (JsonObject json : jsonObjects().values()) {
            if (access().canRead(json)) {
                JEVisObject obj = getObject(json.getId());
                if (obj != null && obj.getDeleteTS() == null) {
                    list.add(obj);
                }
            }
        }
        return list;
    }

    /**
     * Like the client: the class of a link is the class of the linked object, and with {@code addheirs} objects of
     * all heirs of the class are included.
     */
    @Override
    public List<JEVisObject> getObjects(JEVisClass jevisClass, boolean addheirs) throws JEVisException {
        List<JEVisObject> list = new ArrayList<>();
        if (jevisClass == null) {
            return list;
        }
        Set<String> names = new HashSet<>();
        names.add(jevisClass.getName());
        if (addheirs) {
            List<JEVisClass> heirs = jevisClass.getHeirs();
            if (heirs != null) {
                for (JEVisClass heir : heirs) {
                    names.add(heir.getName());
                }
            }
        }
        for (JEVisObject obj : getObjects()) {
            JEVisClass objClass = obj.getJEVisClass();
            if (objClass != null && names.contains(objClass.getName())) {
                list.add(obj);
            }
        }
        return list;
    }

    @Override
    public List<JEVisObject> getDeletedObjects() {
        List<JEVisObject> list = new ArrayList<>();
        for (JsonObject json : jsonObjects().values()) {
            if (access().canRead(json)) {
                JEVisObject obj = getObject(json.getId());
                if (obj != null && obj.getDeleteTS() != null) {
                    list.add(obj);
                }
            }
        }
        return list;
    }

    /**
     * The objects a ROOT relationship of one of the user's read groups points to, without those below another root
     * (like the client).
     */
    @Override
    public List<JEVisObject> getRootObjects() {
        Set<Long> groups = new HashSet<>(access().getReadGroups());
        for (JEVisRelationship rel : getRelationships()) {
            if (rel.getStartID() == userID && isType(rel, JEVisConstants.ObjectRelationship.MEMBER_READ)) {
                groups.add(rel.getEndID());
            }
        }
        List<JEVisObject> roots = new ArrayList<>();
        for (JEVisRelationship rel : getRelationships()) {
            if (isType(rel, JEVisConstants.ObjectRelationship.ROOT) && groups.contains(rel.getStartID())) {
                JEVisObject root = getObject(rel.getEndID());
                if (root != null && !roots.contains(root)) {
                    roots.add(root);
                }
            }
        }
        List<JEVisObject> result = new ArrayList<>();
        for (JEVisObject root : roots) {
            if (!parentIsInList(roots, root, new HashSet<Long>())) {
                result.add(root);
            }
        }
        return result;
    }

    private static boolean isType(JEVisRelationship rel, int type) {
        try {
            return rel.isType(type);
        } catch (JEVisException ex) {
            return false;
        }
    }

    private boolean parentIsInList(List<JEVisObject> list, JEVisObject obj, Set<Long> visited) {
        if (!visited.add(obj.getID())) {
            return false;
        }
        try {
            List<JEVisObject> parents = obj.getParents();
            if (!parents.isEmpty()) {
                if (list.contains(parents.get(0))) {
                    return true;
                }
                return parentIsInList(list, parents.get(0), visited);
            }
        } catch (JEVisException ex) {
            logger.debug("Could not resolve parents of {}", obj.getID(), ex);
        }
        return false;
    }

    // ---------------------------------------------------------------- classes

    @Override
    public JEVisClass getJEVisClass(String name) {
        if (name == null) {
            return null;
        }
        return classes().get(name);
    }

    @Override
    public List<JEVisClass> getJEVisClasses() {
        return new ArrayList<JEVisClass>(classes().values());
    }

    @Override
    public List<JEVisType> getTypes(String className) throws JEVisException {
        JEVisClass jclass = getJEVisClass(className);
        if (jclass == null) {
            return new ArrayList<>();
        }
        return jclass.getTypes();
    }

    @Override
    public List<JEVisClassRelationship> getClassRelationships() throws JEVisException {
        Set<JEVisClassRelationship> set = new LinkedHashSet<>();
        for (LocalClass jclass : classes().values()) {
            set.addAll(jclass.getRelationships());
        }
        return new ArrayList<>(set);
    }

    @Override
    public List<JEVisClassRelationship> getClassRelationships(String jclass) throws JEVisException {
        JEVisClass c = getJEVisClass(jclass);
        if (c == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(c.getRelationships());
    }

    /**
     * Icon of a class: the image file named like the class below the class directory, as {@code GET
     * /classes/{name}/icon} delivers it. {@code null} if there is none.
     */
    BufferedImage getClassIcon(String name) {
        if (name == null) {
            return null;
        }
        BufferedImage cached = iconCache.get(name);
        if (cached != null) {
            return cached;
        }
        try {
            File icon = findIcon(Config.getClassDir(), name, 0);
            if (icon != null) {
                BufferedImage image = ImageIO.read(icon);
                if (image != null) {
                    iconCache.put(name, image);
                }
                return image;
            }
        } catch (Exception ex) {
            logger.debug("Could not read icon of class {}", name, ex);
        }
        return null;
    }

    private static File findIcon(File dir, String name, int depth) {
        if (dir == null || depth > 10) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            String fileName = file.getName();
            int dot = fileName.lastIndexOf('.');
            if (file.isFile() && dot > 0) {
                String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
                if ((ext.equals("png") || ext.equals("jpg") || ext.equals("gif"))
                        && name.equalsIgnoreCase(fileName.substring(0, dot))) {
                    return file;
                }
            }
        }
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findIcon(file, name, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- relationships

    @Override
    public List<JEVisRelationship> getRelationships(int type) {
        List<JEVisRelationship> list = new ArrayList<>();
        for (JEVisRelationship rel : getRelationships()) {
            if (isType(rel, type)) {
                list.add(rel);
            }
        }
        return list;
    }

    /**
     * @return the visible relationships starting or ending at the object
     */
    @Override
    public List<JEVisRelationship> getRelationships(long objectID) {
        loadRelationships();
        List<JEVisRelationship> list = relationshipsByObject.get(objectID);
        return list != null ? new ArrayList<>(list) : new ArrayList<JEVisRelationship>();
    }

    /**
     * @return all relationships visible to the user, see {@link LocalAccess}
     */
    @Override
    public List<JEVisRelationship> getRelationships() {
        loadRelationships();
        return new ArrayList<>(relationships);
    }

    // ---------------------------------------------------------------- user

    @Override
    public synchronized JEVisUser getCurrentUser() {
        if (user == null) {
            user = new LocalUser(this, sql.getCurrentUser());
        }
        return user;
    }

    // ---------------------------------------------------------------- attributes

    /**
     * Loads the attributes of all readable objects in one query (like the client's preload).
     */
    @Override
    public void getAttributes() throws JEVisException {
        synchronized (this) {
            if (allAttributesLoaded) {
                return;
            }
            allAttributesLoaded = true;
        }
        Map<Long, List<JsonAttribute>> byObject = new HashMap<>();
        for (JsonAttribute att : sql.getAttributeTable().getAllAttributes()) {
            List<JsonAttribute> list = byObject.get(att.getObjectID());
            if (list == null) {
                list = new ArrayList<>();
                byObject.put(att.getObjectID(), list);
            }
            list.add(att);
        }
        for (JsonObject json : jsonObjects().values()) {
            if (!attributeCache.containsKey(json.getId()) && access().canRead(json)) {
                List<JsonAttribute> rows = byObject.get(json.getId());
                attributeCache.put(json.getId(), wrapAttributes(json, rows != null ? rows : new ArrayList<JsonAttribute>()));
            }
        }
    }

    /**
     * Attributes of the object (not resolving links, see {@link JEVisObject#getAttributes()}); empty if the object
     * is not readable.
     */
    @Override
    public List<JEVisAttribute> getAttributes(long objectID) {
        List<JEVisAttribute> cached = attributeCache.get(objectID);
        if (cached != null) {
            return cached;
        }
        JsonObject json = jsonObjects().get(objectID);
        if (json == null || !access().canRead(json)) {
            return new ArrayList<>();
        }
        List<JsonAttribute> rows;
        try {
            rows = sql.getAttributeTable().getAttributes(objectID);
        } catch (JEVisException ex) {
            logger.error("Could not load attributes of object {}", objectID, ex);
            return new ArrayList<>();
        }
        List<JEVisAttribute> atts = wrapAttributes(json, rows);
        List<JEVisAttribute> existing = attributeCache.putIfAbsent(objectID, atts);
        return existing != null ? existing : atts;
    }

    /**
     * Same as {@link SQLDataSource#getAttributes(long)}: one attribute per type of the class, in type order; types
     * without a database row get a default attribute without samples. Without types of the class the list is empty.
     */
    private List<JEVisAttribute> wrapAttributes(JsonObject object, List<JsonAttribute> rows) {
        List<JEVisAttribute> result = new ArrayList<>();
        LocalClass jclass = classes().get(object.getJevisClass());
        if (jclass == null || jclass.getJson().getTypes() == null) {
            logger.debug("Empty type list for class: {}", object.getJevisClass());
            return Collections.synchronizedList(result);
        }
        for (JsonType type : jclass.getJson().getTypes()) {
            boolean exists = false;
            for (JsonAttribute att : rows) {
                if (type.getName().equals(att.getType())) {
                    exists = true;
                    result.add(new LocalAttribute(this, att));
                }
            }
            if (!exists) {
                result.add(new LocalAttribute(this, defaultAttribute(object.getId(), type)));
            }
        }
        return Collections.synchronizedList(result);
    }

    static JsonAttribute defaultAttribute(long objectID, JsonType type) {
        JsonAttribute att = new JsonAttribute();
        att.setObjectID(objectID);
        att.setType(type.getName());
        att.setBegins("");
        att.setEnds("");
        att.setDisplaySampleRate(Period.ZERO.toString());
        att.setInputSampleRate("");
        att.setSampleCount(0);
        att.setPrimitiveType(type.getPrimitiveType());
        try {
            JsonUnit unit = JsonFactory.buildUnit(new JEVisUnitImp(AbstractUnit.ONE));
            att.setDisplayUnit(unit);
            att.setInputUnit(unit);
        } catch (Exception ex) {
            logger.debug("Could not build default unit", ex);
        }
        return att;
    }

    // ---------------------------------------------------------------- samples

    /**
     * Raw samples like {@code GET .../samples?from&until&limit}; both bounds inclusive, {@code null} for open.
     */
    List<JEVisSample> getSamples(LocalAttribute att, DateTime from, DateTime until, long limit) {
        JsonObject obj = checkSampleAccess(att);
        try {
            List<JsonSample> jsons = sql.getSamples(att.getObjectID(), att.getName(), toRestTimestamp(from), toRestTimestamp(until), limit);
            return wrapSamples(obj, att, jsons);
        } catch (JEVisException ex) {
            throw new IllegalStateException("Could not load samples for object " + att.getObjectID()
                    + " attribute '" + att.getName() + "'", ex);
        }
    }

    /**
     * Samples like {@code GET .../samples?from&until&cwd&ap&mm&tz}. As in the client the parameters are only used if
     * {@code from} and all strings are set; empty or {@code NONE} aggregation and manipulation return the raw samples,
     * anything else goes through {@link JsonSampleGenerator} exactly like {@code ResourceSample}.
     */
    List<JEVisSample> getSamples(LocalAttribute att, DateTime from, DateTime until, boolean customWorkDay,
                                 String aggregationPeriod, String manipulationMode, String timeZone) {
        if (from == null || aggregationPeriod == null || manipulationMode == null || timeZone == null) {
            return getSamples(att, from, until, Long.MAX_VALUE);
        }
        if ((aggregationPeriod.equals("") && manipulationMode.equals(""))
                || (aggregationPeriod.equals("NONE") && manipulationMode.equals("NONE"))) {
            return getSamples(att, from, until, REST_DEFAULT_SAMPLE_LIMIT);
        }
        JsonObject obj = checkSampleAccess(att);
        AggregationPeriod ap = AggregationPeriod.parseAggregation(aggregationPeriod);
        ManipulationMode mm = ManipulationMode.parseManipulation(manipulationMode);
        DateTimeZone dateTimeZone = DateTimeZone.forID(timeZone);
        JsonSampleGenerator sg = new JsonSampleGenerator(sql, obj, att.getJson(), toRestTimestamp(from), toRestTimestamp(until),
                customWorkDay, mm, ap, dateTimeZone);
        return wrapSamples(obj, att, sg.getAggregatedSamples());
    }

    /**
     * The checks of {@code GET .../samples}: the object must be readable; the user's own {@code Enabled} and
     * {@code Sys Admin} attributes are not accessible.
     */
    private JsonObject checkSampleAccess(LocalAttribute att) {
        JsonObject obj = jsonObjects().get(att.getObjectID());
        if (obj == null) {
            throw new IllegalStateException("Object not found: " + att.getObjectID());
        }
        if ("User".equals(obj.getJevisClass()) && obj.getId() == userID) {
            if ("Enabled".equals(att.getName()) || "Sys Admin".equals(att.getName())) {
                throw new IllegalStateException("Attribute '" + att.getName() + "' of the own user is not accessible");
            }
        } else if (!access().canRead(obj)) {
            throw new IllegalStateException("Object is not accessible: " + att.getObjectID());
        }
        return obj;
    }

    private List<JEVisSample> wrapSamples(JsonObject obj, LocalAttribute att, List<JsonSample> jsons) {
        List<JEVisSample> samples = new ArrayList<>();
        if (jsons == null) {
            return samples;
        }
        boolean mask = isPasswordAttribute(obj, att.getJson().getPrimitiveType(), att.getName());
        for (JsonSample json : jsons) {
            if (mask) {
                json.setValue(LocalSample.PASSWORD_MASK);
            }
            try {
                samples.add(new LocalSample(this, json, att));
            } catch (Exception ex) {
                throw new IllegalStateException("Could not parse sample " + json + " for object "
                        + att.getObjectID() + " attribute '" + att.getName() + "'", ex);
            }
        }
        return samples;
    }

    /**
     * Reads the file of a file sample from the file directory, like {@code GET .../samples/files/{ts}}.
     *
     * @return the file or {@code null} if it is missing
     */
    JEVisFile readFile(LocalAttribute att, DateTime ts, String fileName) {
        if (fileName == null || fileName.contains("..") || att.getName().contains("..")
                || att.getName().contains("/") || att.getName().contains("\\")) {
            return null;
        }
        checkSampleAccess(att);
        File file = new File(Config.getFileDir().getAbsolutePath()
                + File.separator + att.getObjectID()
                + File.separator + att.getName()
                + File.separator + DateTimeFormat.forPattern("yyyyMMddHHmmss").withZoneUTC().print(ts)
                + "_" + fileName);
        if (!file.exists() || !file.canRead()) {
            logger.warn("File of sample {}:{} at {} is missing or not readable", att.getObjectID(), att.getName(), ts);
            return null;
        }
        try (InputStream is = new FileInputStream(file)) {
            byte[] bytes = org.apache.commons.io.IOUtils.toByteArray(is);
            return new JEVisFileImp(fileName, bytes);
        } catch (IOException ex) {
            logger.error("Could not read file {}", file, ex);
            return null;
        }
    }

    // ---------------------------------------------------------------- connection

    /**
     * Not supported: the data source is created from an authenticated request.
     */
    @Override
    public boolean connect(String username, String password) {
        throw new UnsupportedOperationException("connect is not supported: the server-side data source uses the request login");
    }

    /**
     * Not supported: the data source is created from an authenticated request.
     */
    @Override
    public boolean connect(String token) {
        throw new UnsupportedOperationException("connect is not supported: the server-side data source uses the request login");
    }

    @Override
    public boolean connectWithApiKey(String apiKey) {
        throw new UnsupportedOperationException("connect is not supported: the server-side data source uses the request login");
    }

    /**
     * Drops the in-memory caches. The {@link SQLDataSource} is not closed, its owner closes it.
     */
    @Override
    public boolean disconnect() {
        clearCache();
        return true;
    }

    @Override
    public boolean reconnect() {
        return true;
    }

    @Override
    public JEVisInfo getInfo() {
        return info;
    }

    @Override
    public boolean isConnectionAlive() {
        return true;
    }

    /**
     * Like the client: no unit list.
     */
    @Override
    public List<JEVisUnit> getUnits() {
        return new ArrayList<>();
    }

    // ---------------------------------------------------------------- caches

    /**
     * Loads objects, relationships and classes of the request in one go.
     */
    @Override
    public void preload() {
        jsonObjects();
        loadRelationships();
        classes();
    }

    /**
     * No-op: the data of one request is not reloaded.
     */
    @Override
    public void reloadObjects() {
    }

    /**
     * No-op: the data of one request is not reloaded.
     */
    @Override
    public void reloadAttributes() {
    }

    /**
     * No-op: attribute metadata of one request is not reloaded; samples are always read from the database.
     */
    @Override
    public void reloadAttribute(JEVisAttribute attribute) {
    }

    /**
     * No-op: attribute metadata of one request is not reloaded; samples are always read from the database.
     */
    @Override
    public void reloadAttribute(JEVisObject object) {
    }

    /**
     * No-op: the data of one request is not reloaded.
     */
    @Override
    public void reloadObject(JEVisObject object) {
    }

    /**
     * Drops all in-memory objects, attributes, relationships and classes of this data source; they are loaded again on
     * the next access (from the request cache of the {@link SQLDataSource}).
     */
    @Override
    public synchronized void clearCache() {
        objectCache.clear();
        attributeCache.clear();
        iconCache.clear();
        jsonObjects = null;
        access = null;
        relationships = null;
        relationshipsByObject = null;
        classes = null;
        allAttributesLoaded = false;
        user = null;
    }

    /**
     * No-op: the rights of one request do not change.
     */
    @Override
    public void updateAccessControl() {
    }

    // ---------------------------------------------------------------- write operations

    @Override
    public JEVisClass buildClass(String name) {
        throw readOnly("buildClass");
    }

    @Override
    public JEVisObject buildLink(String name, JEVisObject parent, JEVisObject linkedObject) {
        throw readOnly("buildLink");
    }

    @Override
    public JEVisClassRelationship buildClassRelationship(String fromClass, String toClass, int type) {
        throw readOnly("buildClassRelationship");
    }

    @Override
    public JEVisRelationship buildRelationship(Long fromObject, Long toObject, int type) {
        throw readOnly("buildRelationship");
    }

    @Override
    public boolean deleteObject(long objectID, boolean deleteForever) {
        throw readOnly("deleteObject");
    }

    @Override
    public JEVisObject restoreObject(JEVisObject object, JEVisObject targetParent) {
        throw readOnly("restoreObject");
    }

    @Override
    public int emptyRecycleBin() {
        throw readOnly("emptyRecycleBin");
    }

    @Override
    public boolean deleteClass(String jclass) {
        throw readOnly("deleteClass");
    }

    @Override
    public boolean deleteRelationship(Long fromObject, Long toObject, int type) {
        throw readOnly("deleteRelationship");
    }

    @Override
    public boolean deleteClassRelationship(String fromClass, String toClass, int type) {
        throw readOnly("deleteClassRelationship");
    }

    @Override
    public String toString() {
        return "LocalDataSource [user: " + userID + "]";
    }
}
