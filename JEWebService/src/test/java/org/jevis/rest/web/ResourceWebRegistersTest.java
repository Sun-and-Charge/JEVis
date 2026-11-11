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

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import jakarta.ws.rs.core.Response;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.*;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

public class ResourceWebRegistersTest {

    private static final LocalDate TODAY = new LocalDate(2026, 11, 11);
    private static final String EMI = "Electricity Measurement Instrument";
    private static final String SPECIAL_EMI = "Special Electricity Measurement Instrument";
    private static final String MI = ResourceWebRegisters.CLASS_MEASUREMENT_INSTRUMENT;
    private static final String MD = ResourceWebRegisters.CLASS_MEASUREMENT_DIRECTORY;
    private static final String BD = ResourceWebRegisters.CLASS_BASE_DATA;
    private static final String BE = ResourceWebRegisters.CLASS_EQUIPMENT;

    // ---------------------------------------------------------------- classes and translations

    private static JsonType type(String cls, String name, int primitive, String gui, int position) {
        JsonType type = new JsonType();
        type.setJevisclass(cls);
        type.setName(name);
        type.setPrimitiveType(primitive);
        type.setGuiType(gui);
        type.setGUIPosition(position);
        return type;
    }

    private static JsonJEVisClass jclass(String name, String inherits, List<JsonType> types) {
        JsonJEVisClass jclass = new JsonJEVisClass();
        jclass.setName(name);
        List<JsonClassRelationship> relationships = new ArrayList<>();
        if (inherits != null) {
            JsonClassRelationship rel = new JsonClassRelationship();
            rel.setStart(name);
            rel.setEnd(inherits);
            rel.setType(JEVisConstants.ClassRelationship.INHERIT);
            relationships.add(rel);
        }
        jclass.setRelationships(relationships);
        jclass.setTypes(new ArrayList<>(types));
        return jclass;
    }

    private static final List<JsonType> METER_TYPES = Arrays.asList(
            type(MI, "Remarks", 0, "Text Area", 23),
            type(MI, "Online ID", 0, "Object Target", 4),
            type(MI, "Decimal Places", 2, "Text", 6),
            type(MI, "Serial Number", 0, "Text", 13),
            type(MI, "Picture", 3, "File Selector", 17),
            type(MI, "Installation Date", 0, "Date", 20),
            type(MI, "Verification Date", 0, "Date", 22),
            type(MI, "Password", 0, "Password", 30));

    private static List<JsonType> plus(List<JsonType> types, JsonType... more) {
        List<JsonType> list = new ArrayList<>(types);
        list.addAll(Arrays.asList(more));
        return list;
    }

    private static final List<JsonType> ELECTRICITY_TYPES = plus(METER_TYPES, type(EMI, "Current Transformer", 1, "Number", 5));
    private static final List<JsonType> EQUIPMENT_TYPES = Arrays.asList(
            type(BE, "Operating", 4, "Check Box", 3),
            type(BE, "Manufacturer", 0, "Text", 1),
            type(BE, "Nominal Power", 1, "Number", 2));

    private static ResourceWebRegisters.Classes classes() {
        Map<String, JsonJEVisClass> map = new HashMap<>();
        for (JsonJEVisClass c : Arrays.asList(
                jclass(MD, null, Collections.emptyList()),
                jclass(MI, null, METER_TYPES),
                jclass(EMI, MI, ELECTRICITY_TYPES),
                jclass(SPECIAL_EMI, EMI, ELECTRICITY_TYPES),
                jclass(BD, "Data", Arrays.asList(type(BD, "Value", 1, "Number", 1), type(BD, "Period", 0, "Period", 2))),
                jclass(BE, null, EQUIPMENT_TYPES),
                jclass("Pump", BE, EQUIPMENT_TYPES))) {
            map.put(c.getName(), c);
        }
        return new ResourceWebRegisters.Classes(map);
    }

    private static Map<String, String> names(String... pairs) {
        Map<String, String> names = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) names.put(pairs[i], pairs[i + 1]);
        return names;
    }

    private static JsonI18nClass i18n(String cls, Map<String, String> names, JsonI18nType... types) {
        JsonI18nClass i18n = new JsonI18nClass();
        i18n.setJevisclass(cls);
        i18n.setNames(names);
        i18n.setTypes(new ArrayList<>(Arrays.asList(types)));
        return i18n;
    }

    private static JsonI18nType i18nType(String type, Map<String, String> names) {
        JsonI18nType i18n = new JsonI18nType();
        i18n.setType(type);
        i18n.setNames(names);
        return i18n;
    }

    private static ResourceWebRegisters.Translations translations(String lang) {
        return new ResourceWebRegisters.Translations(Arrays.asList(
                i18n(EMI, names("de", "Stromzähler", "en", "Electricity meter")),
                i18n(MI, names("de", "Messgerät", "en", "Meter"),
                        i18nType("Serial Number", names("de", "Seriennummer", "en", "Serial number")),
                        i18nType("Remarks", names("de", "Bemerkungen"))),
                i18n("Pump", names("en", "Pump")),
                i18n(BE, names("de", "Gebäudeausrüstung"), i18nType("Manufacturer", names("de", "Hersteller")))),
                lang, classes());
    }

    // ---------------------------------------------------------------- objects

    private static JsonObject obj(long id, String name, String jevisClass, long parent) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName(name);
        object.setJevisClass(jevisClass);
        object.setParent(parent);
        return object;
    }

    /* Site 1 > meters 10 > meter B 12 > sub meter 14; meters 10 > hall 11 > meter A 13; hidden meter 15, meter in the
       recycle bin 16; data 20 (target), hidden data 21; meters 30 without building; base data folder 40 > 41, 42;
       pump 50, equipment 51; hidden folder 60 > meter 61 */
    private static final JsonObject SITE = obj(1, "Site A", ResourceWebNavigation.CLASS_BUILDING, 0);
    private static final JsonObject METERS = obj(10, "Meters", MD, 1);
    private static final JsonObject HALL = obj(11, "Hall", "Electricity Measurement Directory", 10);
    private static final JsonObject METER_B = obj(12, "Meter B", EMI, 10);
    private static final JsonObject METER_A = obj(13, "Meter A", MI, 11);
    private static final JsonObject SUB_METER = obj(14, "Sub meter", SPECIAL_EMI, 12);
    private static final JsonObject METER_HIDDEN = obj(15, "Meter hidden", EMI, 10);
    private static final JsonObject METER_BIN = obj(16, "Meter bin", EMI, 10);
    private static final JsonObject DATA = obj(20, "Meter B data", "Data", 1);
    private static final JsonObject DATA_HIDDEN = obj(21, "Hidden data", "Data", 1);
    private static final JsonObject METERS_2 = obj(30, "Main meters", MD, 0);
    private static final JsonObject PRICES = obj(40, "Prices", "Base Data Directory", 1);
    private static final JsonObject GAS_PRICE = obj(41, "Gas price", BD, 40);
    private static final JsonObject POWER_PRICE = obj(42, "Power price", BD, 40);
    private static final JsonObject PUMP = obj(50, "Pump 1", "Pump", 1);
    private static final JsonObject ENGINE = obj(51, "Engine", BE, 1);
    private static final JsonObject HIDDEN_FOLDER = obj(60, "Hidden folder", "Folder", 10);
    private static final JsonObject METER_IN_HIDDEN = obj(61, "Meter in hidden folder", EMI, 60);

    static {
        METER_BIN.setDeleteTS("2026-11-01T00:00:00.000+01:00");
    }

    private static final List<JsonObject> ALL = Arrays.asList(SITE, METERS, HALL, METER_B, METER_A, SUB_METER,
            METER_HIDDEN, METER_BIN, DATA, DATA_HIDDEN, METERS_2, PRICES, GAS_PRICE, POWER_PRICE, PUMP, ENGINE,
            HIDDEN_FOLDER, METER_IN_HIDDEN);

    private static ResourceWebModules.Lookup lookup() {
        List<JsonObject> readable = new ArrayList<>(ALL);
        readable.removeAll(Arrays.asList(METER_HIDDEN, DATA_HIDDEN, HIDDEN_FOLDER));
        Map<Long, Long> parents = new HashMap<>();
        for (JsonObject o : ALL) parents.put(o.getId(), o.getParent());
        return new ResourceWebModules.Lookup(ALL, readable, parents);
    }

    private static JsonAttribute att(String name, String value, String unit) {
        JsonAttribute attribute = new JsonAttribute();
        attribute.setType(name);
        if (value != null) {
            JsonSample sample = new JsonSample();
            sample.setTs("2026-11-10T23:45:00.000+01:00");
            sample.setValue(value);
            attribute.setLatestValue(sample);
        }
        JsonUnit jsonUnit = new JsonUnit();
        jsonUnit.setLabel(unit != null ? unit : "");
        attribute.setDisplayUnit(jsonUnit);
        return attribute;
    }

    private static Map<String, JsonAttribute> atts(JsonAttribute... attributes) {
        Map<String, JsonAttribute> map = new HashMap<>();
        for (JsonAttribute attribute : attributes) map.put(attribute.getType(), attribute);
        return map;
    }

    private static final class TestData implements ResourceWebRegisters.Data {
        final Map<Long, Map<String, JsonAttribute>> attributes = new HashMap<>();

        @Override
        public Map<String, JsonAttribute> attributes(long id) {
            return attributes.getOrDefault(id, Collections.emptyMap());
        }

        @Override
        public Integer decimals(long id, Map<Long, Integer> meterDecimalsByTarget) {
            return meterDecimalsByTarget.get(id);
        }
    }

    private static TestData data() {
        TestData data = new TestData();
        data.attributes.put(12L, atts(att("Online ID", "20:Value", null), att("Serial Number", "A-1", null),
                att("Decimal Places", "3", null), att("Picture", "photo.jpg", null),
                att("Installation Date", "2015-10-01T00:00:00.000+02:00", null),
                att("Verification Date", "2026-11-01T00:00:00.000+01:00", null),
                att("Current Transformer", "100.0", "A"), att("Password", "secret", null), att("Remarks", "", null)));
        data.attributes.put(13L, atts(att("Online ID", "21", null), att("Verification Date", "2026-12-31 00:00:00", null)));
        data.attributes.put(14L, atts(att("Online ID", "20", null)));
        data.attributes.put(20L, atts(att("Value", "12345.6", "kWh")));
        data.attributes.put(41L, atts(att("Value", "0.12", "€/kWh"), att("Period", "P1M", null)));
        data.attributes.put(50L, atts(att("Manufacturer", "Maker A", null), att("Operating", "true", null),
                att("Nominal Power", "2.5", "kW")));
        return data;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> json, String key) {
        return (List<Map<String, Object>>) json.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> byName(List<Map<String, Object>> list, String name) {
        for (Map<String, Object> m : list) {
            if (name.equals(m.get("name")) || name.equals(m.get("key"))) return m;
        }
        throw new AssertionError("missing " + name);
    }

    // ---------------------------------------------------------------- tests

    @Test
    public void meterGroupsPerMeasurementDirectoryWithAllReadableMetersBelow() {
        List<Map<String, Object>> groups = ResourceWebRegisters.meterGroups(lookup(), classes(), translations("de"),
                data(), TODAY);
        assertEquals(2, groups.size());
        assertEquals("Main meters", groups.get(0).get("name"));
        assertNull(groups.get(0).get("site"));
        assertTrue(list(groups.get(0), "rows").isEmpty());

        Map<String, Object> group = groups.get(1);
        assertEquals("10", group.get("key"));
        assertEquals("Site A", group.get("name"));
        assertEquals(1L, map(group.get("site")).get("id"));
        List<String> rows = new ArrayList<>();
        for (Map<String, Object> row : list(group, "rows")) rows.add((String) row.get("name"));
        assertEquals(Arrays.asList("Meter A", "Meter B", "Sub meter"), rows);
    }

    @Test
    public void meterColumnsAreTheUnionOfTheTypesSortedByPosition() {
        Map<String, Object> group = ResourceWebRegisters.meterGroups(lookup(), classes(), translations("de"),
                data(), TODAY).get(1);
        List<String> keys = new ArrayList<>();
        for (Map<String, Object> column : list(group, "columns")) keys.add((String) column.get("key"));
        assertEquals(Arrays.asList("Online ID", "Current Transformer", "Decimal Places", "Serial Number", "Picture",
                "Installation Date", "Verification Date", "Remarks"), keys);

        List<Map<String, Object>> columns = list(group, "columns");
        Map<String, Object> serial = columns.get(3);
        assertEquals("Seriennummer", serial.get("name"));
        assertEquals("TEXT", serial.get("type"));
        assertEquals(true, serial.get("visible"));
        assertEquals(13, serial.get("position"));
        assertEquals("TARGET", columns.get(0).get("type"));
        assertEquals("NUMBER", columns.get(1).get("type"));
        assertEquals("A", columns.get(1).get("unit"));
        assertEquals(false, columns.get(1).get("visible"));
        assertEquals("INTEGER", columns.get(2).get("type"));
        assertEquals("FILE", columns.get(4).get("type"));
        assertEquals("DATE", columns.get(5).get("type"));
        assertEquals("TEXTAREA", columns.get(7).get("type"));
        assertEquals("Bemerkungen", columns.get(7).get("name"));
    }

    @Test
    public void meterRowWithTypedValuesTargetLastValueAndVerification() {
        Map<String, Object> group = ResourceWebRegisters.meterGroups(lookup(), classes(), translations("de"),
                data(), TODAY).get(1);
        Map<String, Object> row = byName(list(group, "rows"), "Meter B");
        assertEquals(12L, row.get("id"));
        assertEquals(EMI, row.get("className"));
        assertEquals("Stromzähler", row.get("classLabel"));
        assertEquals("Site A / Meters", row.get("path"));
        assertEquals("ELECTRICITY", row.get("medium"));

        Map<String, Object> values = map(row.get("values"));
        assertEquals("A-1", values.get("Serial Number"));
        assertEquals(3L, values.get("Decimal Places"));
        assertEquals(100.0, values.get("Current Transformer"));
        assertEquals("2015-10-01", values.get("Installation Date"));
        assertEquals(true, map(values.get("Picture")).get("image"));
        assertEquals("photo.jpg", map(values.get("Picture")).get("file"));
        assertEquals(20L, map(values.get("Online ID")).get("id"));
        assertFalse(values.containsKey("Password"));
        assertFalse(values.containsKey("Remarks"));

        Map<String, Object> target = map(row.get("target"));
        assertEquals(20L, target.get("id"));
        assertEquals("Value", target.get("attribute"));
        assertEquals("Meter B data", target.get("name"));
        assertEquals(true, target.get("readable"));

        Map<String, Object> last = map(row.get("last"));
        assertEquals(12345.6, last.get("value"));
        assertEquals("kWh", last.get("unit"));
        assertEquals("2026-11-10T23:45:00.000+01:00", last.get("timestamp"));
        assertEquals(3, last.get("decimals"));

        assertEquals("2026-11-01", map(row.get("verification")).get("due"));
        assertEquals(true, map(row.get("verification")).get("overdue"));
    }

    @Test
    public void meterWithUnreadableTargetAndInheritedMedium() {
        Map<String, Object> group = ResourceWebRegisters.meterGroups(lookup(), classes(), translations("de"),
                data(), TODAY).get(1);
        Map<String, Object> meterA = byName(list(group, "rows"), "Meter A");
        assertNull(meterA.get("medium"));
        assertEquals("Site A / Meters / Hall", meterA.get("path"));
        Map<String, Object> target = map(meterA.get("target"));
        assertEquals(21L, target.get("id"));
        assertNull(target.get("name"));
        assertEquals(false, target.get("readable"));
        assertNull(meterA.get("last"));
        assertEquals("2026-12-31", map(meterA.get("verification")).get("due"));
        assertEquals(false, map(meterA.get("verification")).get("overdue"));

        Map<String, Object> sub = byName(list(group, "rows"), "Sub meter");
        assertEquals("ELECTRICITY", sub.get("medium"));
        assertEquals("Site A / Meters / Meter B", sub.get("path"));
        /* decimal places of meter B, whose Online ID points to the same data */
        assertEquals(3, map(sub.get("last")).get("decimals"));
        assertNull(map(sub.get("verification")).get("due"));
    }

    @Test
    public void translationsFallBackToEnglishThenTechnicalNameAndInheritTypeNames() {
        ResourceWebRegisters.Translations fr = translations("fr");
        assertEquals("Electricity meter", fr.className(EMI));
        assertEquals("Serial number", fr.typeName(SPECIAL_EMI, "Serial Number"));
        assertEquals("Remarks", fr.typeName(EMI, "Remarks"));
        assertEquals("Unknown Class", fr.className("Unknown Class"));
        assertEquals("Seriennummer", translations("de").typeName(SPECIAL_EMI, "serial number"));
        assertEquals("Pump", translations("de").className("Pump"));
    }

    @Test
    public void languageParameter() {
        assertEquals("de", ResourceWebRegisters.language("de-DE"));
        assertEquals("pt", ResourceWebRegisters.language("PT_br"));
        assertEquals("en", ResourceWebRegisters.language(null));
        assertEquals("en", ResourceWebRegisters.language("<script>"));
    }

    @Test
    public void baseDataGroupedByFolderWithLastValueAndPeriod() {
        List<Map<String, Object>> groups = ResourceWebRegisters.baseDataGroups(lookup(), classes(),
                translations("en"), data());
        assertEquals(1, groups.size());
        Map<String, Object> group = groups.get(0);
        assertEquals("40", group.get("key"));
        assertEquals("Prices", group.get("name"));
        assertEquals(1L, map(group.get("site")).get("id"));
        assertTrue(list(group, "columns").isEmpty());
        List<Map<String, Object>> rows = list(group, "rows");
        assertEquals("Gas price", rows.get(0).get("name"));
        assertEquals("Site A / Prices", rows.get(0).get("path"));
        Map<String, Object> last = map(rows.get(0).get("last"));
        assertEquals(0.12, last.get("value"));
        assertEquals("€/kWh", last.get("unit"));
        assertEquals("P1M", last.get("period"));
        assertNull(rows.get(1).get("last"));
    }

    @Test
    public void equipmentGroupedByClassWithAllTypesAsColumns() {
        List<Map<String, Object>> groups = ResourceWebRegisters.equipmentGroups(lookup(), classes(),
                translations("de"), data());
        assertEquals(2, groups.size());
        assertEquals("Gebäudeausrüstung", groups.get(0).get("name"));
        assertEquals(BE, groups.get(0).get("key"));
        Map<String, Object> pumps = groups.get(1);
        assertEquals("Pump", pumps.get("name"));
        List<Map<String, Object>> columns = list(pumps, "columns");
        assertEquals("Manufacturer", columns.get(0).get("key"));
        assertEquals("Hersteller", columns.get(0).get("name"));
        assertEquals("kW", columns.get(1).get("unit"));
        assertEquals("BOOLEAN", columns.get(2).get("type"));
        for (Map<String, Object> column : columns) assertEquals(true, column.get("visible"));
        Map<String, Object> values = map(list(pumps, "rows").get(0).get("values"));
        assertEquals("Maker A", values.get("Manufacturer"));
        assertEquals(2.5, values.get("Nominal Power"));
        assertEquals(true, values.get("Operating"));
        assertFalse(list(pumps, "rows").get(0).containsKey("medium"));
    }

    @Test
    public void moduleCountsCountReadableObjects() {
        Map<String, Object> counts = ResourceWebRegisters.moduleCounts(Arrays.asList(SITE, METER_B, METER_A, SUB_METER,
                METER_BIN, GAS_PRICE, PUMP, ENGINE, DATA), classes());
        assertEquals(3, counts.get("meters"));
        assertEquals(1, counts.get("basedata"));
        assertEquals(2, counts.get("equipment"));
    }

    @Test
    public void belowFollowsReadableObjectsOnly() {
        List<JsonObject> meters = ResourceWebRegisters.below(METERS,
                classes().withHeirs(MI), lookup());
        assertEquals(3, meters.size());
        assertTrue(meters.containsAll(Arrays.asList(METER_A, METER_B, SUB_METER)));
        assertFalse(meters.contains(METER_IN_HIDDEN));
        assertFalse(meters.contains(METER_BIN));
    }

    @Test
    public void registerObjectsAreReadableMetersBaseDataAndEquipment() {
        ResourceWebModules.Lookup lookup = lookup();
        ResourceWebRegisters.Classes classes = classes();
        assertEquals(SUB_METER, ResourceWebRegisters.registerObject(lookup, classes, SUB_METER.getId()));
        assertEquals(GAS_PRICE, ResourceWebRegisters.registerObject(lookup, classes, GAS_PRICE.getId()));
        assertEquals(PUMP, ResourceWebRegisters.registerObject(lookup, classes, PUMP.getId()));
        assertNull(ResourceWebRegisters.registerObject(lookup, classes, METER_HIDDEN.getId()));
        assertNull(ResourceWebRegisters.registerObject(lookup, classes, METER_BIN.getId()));
        assertNull(ResourceWebRegisters.registerObject(lookup, classes, DATA.getId()));
        assertNull(ResourceWebRegisters.registerObject(lookup, classes, 999L));
    }

    @Test
    public void fileAttributeIsFoundByItsDecodedName() {
        /* the path parameter arrives decoded ("Verification%20Documentation" -> with space), matched without case */
        ResourceWebRegisters.Classes classes = classes();
        assertEquals("Picture", classes.type(SPECIAL_EMI, "picture").getName());
        assertEquals("Serial Number", classes.type(MI, "Serial Number").getName());
        assertNull(classes.type(MI, "Serial%20Number"));
        assertNull(classes.type(MI, null));
    }

    @Test
    public void valuesAreTyped() {
        ResourceWebModules.Lookup lookup = lookup();
        assertEquals(12.5, ResourceWebRegisters.value("NUMBER", "12.5", lookup));
        assertEquals("n/a", ResourceWebRegisters.value("NUMBER", "n/a", lookup));
        assertNull(ResourceWebRegisters.value("NUMBER", "NaN", lookup));
        assertEquals(4L, ResourceWebRegisters.value("INTEGER", "4.0", lookup));
        assertEquals(false, ResourceWebRegisters.value("BOOLEAN", "0", lookup));
        assertEquals("2026-01-31", ResourceWebRegisters.value("DATETIME", "2026-01-31T23:30:00.000+01:00", lookup));
        assertEquals("2026-01-31", ResourceWebRegisters.value("DATE", "31.01.2026", lookup));
        assertEquals("soon", ResourceWebRegisters.value("DATE", "soon", lookup));
        assertEquals(false, map(ResourceWebRegisters.value("FILE", "sheet.pdf", lookup)).get("image"));
        Map<String, Object> target = map(ResourceWebRegisters.value("TARGET", "20:Raw Data;21", lookup));
        assertEquals(20L, target.get("id"));
        assertEquals("Raw Data", target.get("attribute"));
        assertEquals("abc", ResourceWebRegisters.value("TARGET", "abc", lookup));
        assertNull(ResourceWebRegisters.value("TEXT", "  ", lookup));
    }

    @Test
    public void filesInlineOnlyForImagesDetectedFromTheBytes() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        Response inline = ResourceWebRegisters.fileResponse(png, "photo.jpg", true);
        assertEquals("image/png", inline.getMediaType().toString());
        assertTrue(inline.getHeaderString("Content-Disposition").startsWith("inline;"));
        assertEquals("nosniff", inline.getHeaderString("X-Content-Type-Options"));
        assertEquals("private, no-store", inline.getHeaderString("Cache-Control"));

        Response attachment = ResourceWebRegisters.fileResponse(png, "photo.png", false);
        assertTrue(attachment.getHeaderString("Content-Disposition").startsWith("attachment;"));

        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);
        Response script = ResourceWebRegisters.fileResponse(svg, "../evil\".svg", true);
        assertEquals("application/octet-stream", script.getMediaType().toString());
        assertTrue(script.getHeaderString("Content-Disposition").startsWith("attachment; filename=\"evil_.svg\""));

        Response pdf = ResourceWebRegisters.fileResponse("%PDF-1.4".getBytes(StandardCharsets.US_ASCII), "sheet.pdf", true);
        assertEquals("application/pdf", pdf.getMediaType().toString());
        assertTrue(pdf.getHeaderString("Content-Disposition").startsWith("attachment;"));

        assertEquals("image/jpeg", ResourceWebRegisters.imageType(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertNull(ResourceWebRegisters.imageType(new byte[0]));
    }

    @Test
    public void historyLimitAndJson() {
        assertEquals(12, ResourceWebRegisters.historyLimit(0));
        assertEquals(5, ResourceWebRegisters.historyLimit(5));
        assertEquals(100, ResourceWebRegisters.historyLimit(5000));
        JsonSample sample = new JsonSample();
        sample.setTs("2026-11-10T23:45:00.000+01:00");
        sample.setValue("12.5");
        sample.setNote("meter change");
        Map<String, Object> json = ResourceWebRegisters.historyJson("kWh", Collections.singletonList(sample));
        assertEquals("kWh", json.get("unit"));
        Map<String, Object> first = list(json, "samples").get(0);
        assertEquals(12.5, first.get("value"));
        assertEquals("meter change", first.get("note"));
        assertEquals("2026-11-10T23:45:00.000+01:00", first.get("timestamp"));
    }

    @Test
    public void serverAddressFromProxyHeadersOrRequest() {
        URI request = URI.create("http://localhost:8000/JEWebService/v1/web/registers/meters/12/qr.svg");
        Map<String, String> headers = new HashMap<>();
        assertEquals("http://localhost:8000", ResourceWebRegisters.serverAddress(headers::get, request));

        headers.put("X-Forwarded-Proto", "https");
        assertEquals("https://localhost:443", ResourceWebRegisters.serverAddress(headers::get, request));

        headers.put("X-Forwarded-Host", "example.org, proxy.local");
        assertEquals("https://example.org:443", ResourceWebRegisters.serverAddress(headers::get, request));

        headers.put("X-Forwarded-Host", "example.org:8443");
        assertEquals("https://example.org:8443", ResourceWebRegisters.serverAddress(headers::get, request));

        headers.put("X-Forwarded-Host", "evil.org/path?x");
        assertEquals("https://localhost:443", ResourceWebRegisters.serverAddress(headers::get, request));

        headers.put("X-Forwarded-Proto", "javascript");
        headers.remove("X-Forwarded-Host");
        assertEquals("http://localhost:8000", ResourceWebRegisters.serverAddress(headers::get, request));
    }

    @Test
    public void qrPayloadContainsOnlyReadableTargets() {
        ResourceWebModules.Lookup lookup = lookup();
        assertEquals("jevis:meter?v=1&id=12&target=20:Value&server=https%3A%2F%2Fexample.org%3A443",
                ResourceWebRegisters.qrPayload(12, "20", lookup, "https://example.org:443"));
        assertEquals("jevis:meter?v=1&id=13", ResourceWebRegisters.qrPayload(13, "21:Value", lookup, null));
        assertEquals("jevis:meter?v=1&id=14", ResourceWebRegisters.qrPayload(14, null, lookup, ""));
    }

    @Test
    public void qrSvgDecodesToThePayload() throws Exception {
        String payload = ResourceWebRegisters.qrPayload(12, "20:Value", lookup(), "https://example.org:443");
        BitMatrix matrix = ResourceWebRegisters.encode(payload);
        String svg = ResourceWebRegisters.svg(matrix);
        assertTrue(svg.startsWith("<?xml"));
        assertTrue(svg.contains("viewBox=\"0 0 " + matrix.getWidth() + " " + matrix.getHeight() + "\""));
        assertFalse(svg.contains("<script"));

        /* the dark modules of the path, scaled up, decode to the payload */
        int scale = 4;
        int width = matrix.getWidth() * scale;
        int height = matrix.getHeight() * scale;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0xFFFFFFFF);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("M(\\d+) (\\d+)h(\\d+)").matcher(svg);
        while (m.find()) {
            int x0 = Integer.parseInt(m.group(1));
            int y0 = Integer.parseInt(m.group(2));
            int length = Integer.parseInt(m.group(3));
            for (int y = y0 * scale; y < (y0 + 1) * scale; y++) {
                for (int x = x0 * scale; x < (x0 + length) * scale; x++) pixels[y * width + x] = 0xFF000000;
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        assertEquals(payload, new QRCodeReader().decode(bitmap).getText());
    }
}
