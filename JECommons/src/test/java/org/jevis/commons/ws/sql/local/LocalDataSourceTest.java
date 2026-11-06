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

import org.jevis.api.*;
import org.jevis.commons.ws.json.*;
import org.jevis.commons.ws.sql.JEVisUserSQL;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.UserRightManagerForWS;
import org.jevis.commons.ws.sql.tables.AttributeTable;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.jevis.api.JEVisConstants.ObjectRelationship.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests of the server-side data source without a database: rights, tree, classes, attributes and samples are served
 * by a mocked {@link SQLDataSource}.
 * <p>
 * Fixture (user 50 reads group 100, group 200 is not readable):
 * <pre>
 * 1 Directory (owner 100, root of group 100)
 * +-- 2 Data (owner 100)
 * |   +-- 3 Clean Data (owner 100)
 * +-- 4 Data (owner 200)            not readable
 * +-- 5 Data Directory (public, no owner)
 * +-- 6 Link -> 2 (owner 100)
 * +-- 7 Data (owner 100)            in the recycle bin (DELETED_PARENT)
 * 50 User (the user), 100 Group, 200 Group
 * </pre>
 */
public class LocalDataSourceTest {

    static final long USER = 50L;
    static final long GROUP_READ = 100L;
    static final long GROUP_OTHER = 200L;

    private SQLDataSource sql;
    private UserRightManagerForWS um;
    private AttributeTable attributeTable;
    private List<JsonObject> objects;
    private List<JsonRelationship> relationships;

    static JsonObject object(long id, String name, String jclass, boolean isPublic) {
        JsonObject json = new JsonObject();
        json.setId(id);
        json.setName(name);
        json.setJevisClass(jclass);
        json.setisPublic(isPublic);
        return json;
    }

    static JsonRelationship rel(long from, long to, int type) {
        JsonRelationship json = new JsonRelationship();
        json.setFrom(from);
        json.setTo(to);
        json.setType(type);
        return json;
    }

    static JsonType type(String jclass, String name, int primitiveType, int position) {
        JsonType type = new JsonType();
        type.setJevisclass(jclass);
        type.setName(name);
        type.setPrimitiveType(primitiveType);
        type.setGUIPosition(position);
        return type;
    }

    static JsonClassRelationship classRel(String start, String end, int type) {
        JsonClassRelationship rel = new JsonClassRelationship();
        rel.setStart(start);
        rel.setEnd(end);
        rel.setType(type);
        return rel;
    }

    static JsonJEVisClass jclass(String name, List<JsonType> types, JsonClassRelationship... rels) {
        JsonJEVisClass json = new JsonJEVisClass();
        json.setName(name);
        json.setTypes(types);
        json.setRelationships(new ArrayList<>(Arrays.asList(rels)));
        return json;
    }

    static List<JsonJEVisClass> classes() {
        JsonClassRelationship dataDirInherit = classRel("Data Directory", "Directory", JEVisConstants.ClassRelationship.INHERIT);
        JsonClassRelationship cleanInherit = classRel("Clean Data", "Data", JEVisConstants.ClassRelationship.INHERIT);
        JsonClassRelationship dataUnderDir = classRel("Data", "Data Directory", JEVisConstants.ClassRelationship.OK_PARENT);
        List<JsonJEVisClass> list = new ArrayList<>();
        list.add(jclass("Directory", new ArrayList<JsonType>(), dataDirInherit));
        list.add(jclass("Data Directory", new ArrayList<JsonType>(), dataDirInherit, dataUnderDir));
        list.add(jclass("Data", Arrays.asList(
                type("Data", "Value", JEVisConstants.PrimitiveType.DOUBLE, 2),
                type("Data", "Period", JEVisConstants.PrimitiveType.STRING, 1)), cleanInherit, dataUnderDir));
        list.add(jclass("Clean Data", Arrays.asList(type("Clean Data", "Value", JEVisConstants.PrimitiveType.DOUBLE, 1)), cleanInherit));
        list.add(jclass("Link", new ArrayList<JsonType>()));
        list.add(jclass("Group", new ArrayList<JsonType>()));
        list.add(jclass("User", Arrays.asList(
                type("User", "Password", JEVisConstants.PrimitiveType.PASSWORD_PBKDF2, 1),
                type("User", "Enabled", JEVisConstants.PrimitiveType.BOOLEAN, 2))));
        return list;
    }

    static List<JsonObject> fixtureObjects() {
        List<JsonObject> list = new ArrayList<>();
        list.add(object(1, "Root", "Directory", false));
        list.add(object(2, "Meter A", "Data", false));
        list.add(object(3, "Meter A clean", "Clean Data", false));
        list.add(object(4, "Meter B", "Data", false));
        list.add(object(5, "Public dir", "Data Directory", true));
        list.add(object(6, "Link to A", "Link", false));
        JsonObject deleted = object(7, "Old meter", "Data", false);
        deleted.setDeleteTS("2026-01-02");
        deleted.setDeleted("2026-01-02T03:04:05.000Z");
        list.add(deleted);
        list.add(object(USER, "user", "User", false));
        list.add(object(GROUP_READ, "Readers", "Group", false));
        list.add(object(GROUP_OTHER, "Others", "Group", false));
        return list;
    }

    static List<JsonRelationship> fixtureRelationships() {
        return new ArrayList<>(Arrays.asList(
                rel(2, 1, PARENT), rel(3, 2, PARENT), rel(4, 1, PARENT), rel(5, 1, PARENT), rel(6, 1, PARENT),
                rel(7, 1, DELETED_PARENT),
                rel(6, 2, LINK),
                rel(1, GROUP_READ, OWNER), rel(2, GROUP_READ, OWNER), rel(3, GROUP_READ, OWNER),
                rel(4, GROUP_OTHER, OWNER), rel(6, GROUP_READ, OWNER), rel(7, GROUP_READ, OWNER),
                rel(USER, GROUP_READ, MEMBER_READ),
                rel(GROUP_READ, 1, ROOT)));
    }

    @Before
    public void setUp() throws Exception {
        objects = fixtureObjects();
        relationships = fixtureRelationships();
        sql = mock(SQLDataSource.class);
        um = mock(UserRightManagerForWS.class);
        attributeTable = mock(AttributeTable.class);
        when(um.isSysAdmin()).thenReturn(false);
        when(um.getReadGIDS()).thenReturn(new ArrayList<>(Collections.singletonList(GROUP_READ)));
        when(sql.getUserManager()).thenReturn(um);
        when(sql.getCurrentUser()).thenReturn(new JEVisUserSQL(sql, "user", USER, false, true));
        when(sql.getObjects()).thenReturn(objects);
        when(sql.getRelationships()).thenReturn(relationships);
        when(sql.getJEVisClasses()).thenReturn(classes());
        when(sql.getAttributeTable()).thenReturn(attributeTable);
        when(attributeTable.getAttributes(anyLong())).thenReturn(new ArrayList<JsonAttribute>());
    }

    private static Set<Long> ids(Collection<? extends JEVisObject> list) {
        Set<Long> ids = new TreeSet<>();
        for (JEVisObject obj : list) {
            ids.add(obj.getID());
        }
        return ids;
    }

    private static Set<Long> set(Long... ids) {
        return new TreeSet<>(Arrays.asList(ids));
    }

    // ---------------------------------------------------------------- rights

    @Test
    public void getObjectHidesObjectsWithoutReadRight() {
        LocalDataSource ds = new LocalDataSource(sql);
        assertNull("owned by a foreign group", ds.getObject(4L));
        assertNull("foreign group", ds.getObject(GROUP_OTHER));
        assertNull("does not exist", ds.getObject(999L));
        assertNull(ds.getObject(null));
        assertNotNull("owned by read group", ds.getObject(2L));
        assertNotNull("public", ds.getObject(5L));
        assertNotNull("own user object", ds.getObject(USER));
        assertNotNull("read group itself", ds.getObject(GROUP_READ));
        assertSame("cached wrapper", ds.getObject(2L), ds.getObject(2L));
    }

    @Test
    public void sysAdminSeesEverything() throws Exception {
        when(um.isSysAdmin()).thenReturn(true);
        LocalDataSource ds = new LocalDataSource(sql);
        assertNotNull(ds.getObject(4L));
        assertEquals(set(2L, 4L, 5L, 6L), ids(ds.getObject(1L).getChildren()));
    }

    @Test
    public void objectListsAreFilteredAndWithoutRecycleBin() throws Exception {
        LocalDataSource ds = new LocalDataSource(sql);
        assertEquals(set(1L, 2L, 3L, 5L, 6L, USER, GROUP_READ), ids(ds.getObjects()));
        assertEquals(set(7L), ids(ds.getDeletedObjects()));
        JEVisObject deleted = ds.getObject(7L);
        assertNotNull("objects of the bin are readable by ID like in the client", deleted);
        assertEquals(new DateTime(2026, 1, 2, 3, 4, 5, DateTimeZone.UTC).getMillis(), deleted.getDeleteTS().getMillis());
        assertNull(ds.getObject(2L).getDeleteTS());
    }

    @Test
    public void relationshipsNeedBothEndsReadable() {
        LocalDataSource ds = new LocalDataSource(sql);
        for (JEVisRelationship rel : ds.getRelationships()) {
            assertNotEquals(4L, rel.getStartID());
            assertNotEquals(GROUP_OTHER, rel.getEndID());
        }
        assertEquals(relationships.size() - 2, ds.getRelationships().size());
        assertTrue(ds.getRelationships(4L).isEmpty());
        assertEquals(1, ds.getRelationships(ROOT).size());
    }

    @Test
    public void currentUserReadRights() {
        LocalDataSource ds = new LocalDataSource(sql);
        JEVisUser user = ds.getCurrentUser();
        assertEquals(USER, user.getUserID());
        assertTrue(user.canRead(2L));
        assertFalse(user.canRead(4L));
        assertEquals(Long.valueOf(USER), user.getUserObject().getID());
    }

    // ---------------------------------------------------------------- tree

    @Test
    public void childrenFromParentRelationships() throws Exception {
        LocalDataSource ds = new LocalDataSource(sql);
        JEVisObject root = ds.getObject(1L);
        assertEquals("not 4 (no right), not 7 (recycle bin)", set(2L, 5L, 6L), ids(root.getChildren()));
        assertEquals(set(3L), ids(ds.getObject(2L).getChildren()));
        assertTrue(ds.getObject(3L).getChildren().isEmpty());
    }

    @Test
    public void parentsFromParentRelationships() throws Exception {
        LocalDataSource ds = new LocalDataSource(sql);
        assertEquals(Long.valueOf(2L), ds.getObject(3L).getParent().getID());
        assertEquals(set(1L), ids(ds.getObject(5L).getParents()));
        assertNull(ds.getObject(1L).getParent());
        assertTrue("a DELETED_PARENT is no parent", ds.getObject(7L).getParents().isEmpty());
    }

    @Test
    public void childrenOfClassIncludeHeirsAndLinks() throws Exception {
        LocalDataSource ds = new LocalDataSource(sql);
        JEVisClass data = ds.getJEVisClass("Data");
        JEVisClass clean = ds.getJEVisClass("Clean Data");
        assertEquals("link 6 has the class of the linked object", set(2L, 6L), ids(ds.getObject(1L).getChildren(data, false)));
        assertEquals("subclass, the inherit flag is ignored like in the client", set(3L), ids(ds.getObject(2L).getChildren(data, false)));
        assertEquals(set(3L), ids(ds.getObject(2L).getChildren(data, true)));
        assertTrue(ds.getObject(1L).getChildren(clean, true).isEmpty());
        assertEquals(set(5L), ids(ds.getObject(1L).getChildren(ds.getJEVisClass("Directory"), true)));
    }

    @Test
    public void objectsOfClass() throws Exception {
        LocalDataSource ds = new LocalDataSource(sql);
        JEVisClass data = ds.getJEVisClass("Data");
        assertEquals(set(2L, 6L), ids(ds.getObjects(data, false)));
        assertEquals(set(2L, 3L, 6L), ids(ds.getObjects(data, true)));
        assertTrue(ds.getObjects(null, true).isEmpty());
    }

    @Test
    public void linksResolveToTheLinkedObject() throws Exception {
        LocalDataSource ds = new LocalDataSource(sql);
        JEVisObject link = ds.getObject(6L);
        assertEquals(Long.valueOf(2L), link.getLinkedObject().getID());
        assertEquals("Data", link.getJEVisClass().getName());
        assertEquals("Link", link.getJEVisClassName());
    }

    @Test
    public void rootObjects() {
        LocalDataSource ds = new LocalDataSource(sql);
        assertEquals(set(1L), ids(ds.getRootObjects()));
    }

    // ---------------------------------------------------------------- classes

    @Test
    public void classInheritance() throws Exception {
        LocalDataSource ds = new LocalDataSource(sql);
        JEVisClass clean = ds.getJEVisClass("Clean Data");
        assertEquals("Data", clean.getInheritance().getName());
        assertNull(ds.getJEVisClass("Data").getInheritance());
        assertEquals(Collections.singletonList(clean), ds.getJEVisClass("Data").getHeirs());
        assertEquals("heirs of Directory may contain themselves",
                Arrays.asList(ds.getJEVisClass("Data"), ds.getJEVisClass("Data Directory")),
                ds.getJEVisClass("Data Directory").getValidChildren());
        assertTrue(ds.getJEVisClass("Data").isAllowedUnder(ds.getJEVisClass("Data Directory")));
        assertTrue(clean.getType("Value").isInherited());
        assertEquals("types sorted by GUI position", "Period", ds.getJEVisClass("Data").getTypes().get(0).getName());
    }

    // ---------------------------------------------------------------- attributes

    private static JsonUnit unit(String label) {
        JsonUnit unit = new JsonUnit();
        unit.setLabel(label);
        unit.setPrefix("");
        unit.setFormula("");
        return unit;
    }

    private JsonAttribute valueAttribute(long objectID) {
        JsonAttribute att = new JsonAttribute();
        att.setObjectID(objectID);
        att.setType("Value");
        att.setPrimitiveType(JEVisConstants.PrimitiveType.DOUBLE);
        att.setInputSampleRate("PT15M");
        att.setDisplaySampleRate("not a period");
        att.setInputUnit(unit("kWh"));
        att.setDisplayUnit(unit("MWh"));
        att.setBegins("2026-01-01T00:00:00.000Z");
        att.setEnds("2026-01-31T23:45:00.000Z");
        att.setSampleCount(2976);
        JsonSample latest = new JsonSample();
        latest.setTs("2026-01-31T23:45:00.000Z");
        latest.setValue("12.5");
        att.setLatestValue(latest);
        return att;
    }

    @Test
    public void attributesWithUnitsAndSampleRates() throws Exception {
        when(attributeTable.getAttributes(2L)).thenReturn(new ArrayList<>(Collections.singletonList(valueAttribute(2L))));
        LocalDataSource ds = new LocalDataSource(sql);
        JEVisObject meter = ds.getObject(2L);
        List<JEVisAttribute> atts = meter.getAttributes();
        assertEquals("one per type, missing ones as default", 2, atts.size());

        JEVisAttribute value = meter.getAttribute("value");
        assertNotNull("case-insensitive like the client", value);
        assertEquals(Period.minutes(15), value.getInputSampleRate());
        assertEquals("unreadable is ZERO", Period.ZERO, value.getDisplaySampleRate());
        assertEquals("kWh", value.getInputUnit().getLabel());
        assertEquals("MWh", value.getDisplayUnit().getLabel());
        assertEquals(JEVisConstants.PrimitiveType.DOUBLE, value.getPrimitiveType());
        assertEquals(new DateTime(2026, 1, 1, 0, 0, DateTimeZone.UTC).getMillis(), value.getTimestampOfFirstSample().getMillis());
        assertEquals(new DateTime(2026, 1, 31, 23, 45, DateTimeZone.UTC).getMillis(), value.getTimestampOfLastSample().getMillis());
        assertTrue(value.hasSample());
        assertEquals(12.5, value.getLatestSample().getValueAsDouble(), 0.0);

        JEVisAttribute period = meter.getAttribute("Period");
        assertFalse(period.hasSample());
        assertNull(period.getLatestSample());
        assertEquals(Period.ZERO, period.getInputSampleRate());
        assertEquals(JEVisConstants.PrimitiveType.STRING, period.getPrimitiveType());

        assertTrue("no attributes of unreadable objects", ds.getAttributes(4L).isEmpty());
        verify(attributeTable, never()).getAttributes(4L);
    }

    @Test
    public void linkHasAttributesOfLinkedObject() throws Exception {
        when(attributeTable.getAttributes(2L)).thenReturn(new ArrayList<>(Collections.singletonList(valueAttribute(2L))));
        LocalDataSource ds = new LocalDataSource(sql);
        assertEquals(Long.valueOf(2L), ds.getObject(6L).getAttribute("Value").getObjectID());
    }

    // ---------------------------------------------------------------- samples

    private static JsonSample sample(String ts, String value) {
        JsonSample sample = new JsonSample();
        sample.setTs(ts);
        sample.setValue(value);
        sample.setNote("");
        return sample;
    }

    @Test
    public void rawSamplesAreReadWithoutLimitAndRestTimestamps() throws Exception {
        when(attributeTable.getAttributes(2L)).thenReturn(new ArrayList<>(Collections.singletonList(valueAttribute(2L))));
        when(sql.getSamples(eq(2L), eq("Value"), any(), any(), anyLong())).thenReturn(new ArrayList<>(Arrays.asList(
                sample("2026-01-01T00:00:00.000Z", "1.5"), sample("2026-01-01T00:15:00.000Z", "2.5"))));
        LocalDataSource ds = new LocalDataSource(sql);
        JEVisAttribute value = ds.getObject(2L).getAttribute("Value");

        DateTime from = new DateTime(2026, 1, 1, 1, 0, 0, 123, DateTimeZone.forID("Europe/Berlin"));
        DateTime until = from.plusDays(1);
        List<JEVisSample> samples = value.getSamples(from, until);
        assertEquals(2, samples.size());
        assertEquals(1.5, samples.get(0).getValueAsDouble(), 0.0);
        assertEquals(Double.valueOf(2.5), samples.get(1).getValue());
        verify(sql).getSamples(2L, "Value", new DateTime(2026, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC),
                new DateTime(2026, 1, 2, 0, 0, 0, 0, DateTimeZone.UTC), Long.MAX_VALUE);

        value.getAllSamples();
        verify(sql).getSamples(2L, "Value", null, null, Long.MAX_VALUE);
    }

    @Test
    public void emptyAggregationUsesRawSamplesWithRestLimit() throws Exception {
        when(attributeTable.getAttributes(2L)).thenReturn(new ArrayList<>(Collections.singletonList(valueAttribute(2L))));
        LocalDataSource ds = new LocalDataSource(sql);
        JEVisAttribute value = ds.getObject(2L).getAttribute("Value");
        DateTime from = new DateTime(2026, 1, 1, 0, 0, DateTimeZone.UTC);

        value.getSamples(from, from.plusDays(1), false, "NONE", "NONE", "UTC");
        value.getSamples(from, from.plusDays(2), true, "", "", "Europe/Berlin");
        verify(sql).getSamples(2L, "Value", from, from.plusDays(1), LocalDataSource.REST_DEFAULT_SAMPLE_LIMIT);
        verify(sql).getSamples(2L, "Value", from, from.plusDays(2), LocalDataSource.REST_DEFAULT_SAMPLE_LIMIT);

        value.getSamples(null, from, false, "DAILY", "NONE", "UTC");
        verify(sql).getSamples(2L, "Value", null, from, Long.MAX_VALUE);
    }

    @Test
    public void sampleChangesStayInMemory() throws Exception {
        when(attributeTable.getAttributes(2L)).thenReturn(new ArrayList<>(Collections.singletonList(valueAttribute(2L))));
        when(sql.getSamples(eq(2L), eq("Value"), any(), any(), anyLong())).thenReturn(new ArrayList<>(Collections.singletonList(
                sample("2026-01-01T00:00:00.000Z", "100.0"))));
        LocalDataSource ds = new LocalDataSource(sql);
        JEVisSample sample = ds.getObject(2L).getAttribute("Value").getAllSamples().get(0);

        sample.setValue(42.0);
        sample.setNote("limit replaced");
        assertEquals(42.0, sample.getValueAsDouble(), 0.0);
        assertEquals("limit replaced", sample.getNote());
        assertTrue(sample.hasChanged());
        try {
            sample.commit();
            fail("commit must not be possible");
        } catch (UnsupportedOperationException expected) {
            // read-only
        }
        verify(sql, never()).setSamples(anyLong(), anyString(), anyInt(), anyList());
    }

    @Test
    public void passwordsAreMaskedAndOwnStatusIsNotReadable() throws Exception {
        JsonAttribute password = new JsonAttribute();
        password.setObjectID(USER);
        password.setType("Password");
        password.setPrimitiveType(JEVisConstants.PrimitiveType.PASSWORD_PBKDF2);
        JsonSample hash = sample("2026-01-01T00:00:00.000Z", "pbkdf2-hash");
        password.setLatestValue(hash);
        when(attributeTable.getAttributes(USER)).thenReturn(new ArrayList<>(Collections.singletonList(password)));
        when(sql.getSamples(eq(USER), eq("Password"), any(), any(), anyLong())).thenReturn(new ArrayList<>(Collections.singletonList(
                sample("2026-01-01T00:00:00.000Z", "pbkdf2-hash"))));
        LocalDataSource ds = new LocalDataSource(sql);
        JEVisObject user = ds.getObject(USER);

        assertEquals(LocalSample.PASSWORD_MASK, user.getAttribute("Password").getLatestSample().getValueAsString());
        assertEquals("the attribute itself is not changed", "pbkdf2-hash", hash.getValue());
        assertEquals(LocalSample.PASSWORD_MASK, user.getAttribute("Password").getAllSamples().get(0).getValueAsString());
        try {
            user.getAttribute("Enabled").getAllSamples();
            fail("own Enabled is not accessible, like GET .../samples");
        } catch (IllegalStateException expected) {
            // 403 in the web service
        }
    }

    // ---------------------------------------------------------------- read-only

    @Test
    public void writeOperationsAreRejected() throws Exception {
        when(attributeTable.getAttributes(2L)).thenReturn(new ArrayList<>(Collections.singletonList(valueAttribute(2L))));
        LocalDataSource ds = new LocalDataSource(sql);
        final JEVisObject meter = ds.getObject(2L);
        final JEVisAttribute value = meter.getAttribute("Value");
        final JEVisClass data = ds.getJEVisClass("Data");
        List<Runnable> writes = Arrays.asList(
                () -> ds.deleteObject(2L, false),
                () -> ds.buildRelationship(2L, 1L, PARENT),
                () -> ds.buildClass("X"),
                () -> call(() -> meter.buildObject("x", data)),
                () -> call(() -> meter.setName("x")),
                () -> call(meter::commit),
                () -> call(meter::delete),
                () -> call(() -> value.addSamples(new ArrayList<JEVisSample>())),
                () -> call(() -> value.buildSample(new DateTime(), 1.0)),
                () -> call(() -> value.deleteAllSample()),
                () -> call(() -> value.setInputSampleRate(Period.days(1))),
                () -> call(data::commit),
                () -> call(() -> data.getType("Value").setName("x")));
        int i = 0;
        for (Runnable write : writes) {
            try {
                write.run();
                fail("write operation " + i + " did not throw");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("read-only"));
            }
            i++;
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void call(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void requiresAuthenticatedDataSource() {
        SQLDataSource anonymous = mock(SQLDataSource.class);
        new LocalDataSource(anonymous);
    }
}
