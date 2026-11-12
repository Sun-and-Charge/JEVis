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
package org.jevis.commons.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.commons.chart.AnalysisChartType;
import org.jevis.commons.chart.RowDefinition;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.jevis.commons.chart.WebColor;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DashboardReaderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text.replace('\'', '"'));
    }

    private static final String VALUE_WIDGET = "{'uuid':7,'WidgetType':'Value','title':'Widget A','tooltip':'',"
            + "'Title Position':'left','Background Color':'0xffffffff','Font Color':'0x1a2a45ff','fontSize':14.0,"
            + "'fontWeight':'BOLD','fontPosture':'ITALIC','fontUnderlined':true,'xPos':25.0,'yPos':50.0,"
            + "'width':200.0,'height':120.0,'layer':3,'decimals':3,'showValue':true,'shadow':false,'borderSize':1.0,"
            + "'borderColor':'0xd0d4daff','cornerRadius':4.0,"
            + "'dataHandler':{'type':'AnalysisHandler','forcedInterval':'P1M','chartModels':[{'chartId':0,'chartName':'',"
            + "'chartType':'LINE','chartData':[{'id':2001,'attributeString':'Value','name':'Row A','color':'0x00538aff',"
            + "'unitPrefix':'KILO','unitFormula':'Wh','unitLabel':'Wh','aggregationPeriod':'MONTHLY','manipulationMode':'NONE'},"
            + "{'id':2002,'attributeString':'Value','name':'Row B','color':'0x112233ff','unitPrefix':'','unitFormula':'kW','unitLabel':'kW'}]}]},"
            + "'limit':{'source':12,'lowerLimitOffset':0.5,'upperLimitOffset':2.0,'lowerLimit':true,'upperLimit':false,"
            + "'lowerLimitColor':'0xff000080','upperLimitColor':'0x00ff00ff'},"
            + "'percent':{'source':-1,'minFracDigits':0,'maxFracDigits':1,'diff':false},'dimIncompletePeriod':true}";

    @Test
    public void readsTheDashboardWithOldAndNewNames() throws Exception {
        String file = "{'version':'1.2','Dash Board Color':'0xf4f4f4ff','backgroundMode':'stretch','Zoom Factor':98.0,"
                + "'Update Rate':300,'width':1000.0,'height':500.0,'Data Period':'P1W','Widget':["
                + "{'uuid':3,'WidgetType':'Title','title':'Header','layer':5},"
                + VALUE_WIDGET + ","
                + "{'widgetType':'Title','title':'No id','layer':1}]}";
        DashboardDefinition d = new DashboardReader().readDashboardFile(json(file));
        assertEquals("#f4f4f4", d.getBackgroundColor());
        assertEquals("STRETCH", d.getBackgroundMode());
        assertEquals("FIT_WIDTH", d.getZoomValue());
        assertEquals(300, d.getUpdateRate());
        assertEquals("P1W", d.getTimeFrame());
        assertEquals(1000d, d.getWidth(), 0);

        List<WidgetDefinition> widgets = d.getWidgets();
        assertEquals(3, widgets.size());
        /* sorted by layer, the widget without uuid gets the next free one */
        assertEquals(8, widgets.get(0).getUuid());
        assertEquals(7, widgets.get(1).getUuid());
        assertEquals(3, widgets.get(2).getUuid());

        Map<String, Object> config = d.findWidget(7).getConfig();
        assertTrue((Boolean) config.get("dimIncompletePeriod"));
        assertNull(config.get("percent"));
        @SuppressWarnings("unchecked")
        Map<String, Object> limit = (Map<String, Object>) config.get("limit");
        assertEquals("12", limit.get("source"));
        @SuppressWarnings("unchecked")
        Map<String, Object> lower = (Map<String, Object>) limit.get("lower");
        assertEquals(true, lower.get("enabled"));
        assertEquals(0.5, lower.get("offset"));
        assertNull(lower.get("value"));
        assertEquals("rgba(255,0,0,0.502)", lower.get("color"));
    }

    @Test
    public void readsTheCommonWidgetSettingsAndTheDataHandler() throws Exception {
        WidgetDefinition w = new DashboardReader().readWidget(json(VALUE_WIDGET), null);
        assertEquals("Value", w.getType());
        assertEquals("Widget A", w.getTitle());
        assertEquals("CENTER_LEFT", w.getTitlePosition());
        assertEquals("#ffffff", w.getBackground());
        assertEquals("#1a2a45", w.getFontColor());
        assertEquals("BOLD", w.getFontWeight());
        assertTrue(w.isItalic());
        assertTrue(w.isUnderline());
        assertFalse(w.isShadow());
        assertEquals("#d0d4da", w.getBorderColor());
        assertEquals(25d, w.getX(), 0);
        assertEquals(120d, w.getHeight(), 0);
        assertEquals(3, w.getLayer());
        /* decimals other than 2 without decimalsAuto count as explicit (#1194) */
        assertFalse(w.isDecimalsAuto());
        assertEquals("P1M", w.getTimeFrame());

        List<RowDefinition> rows = w.getRows();
        assertEquals(2, rows.size());
        assertEquals("0-2001-Value", rows.get(0).getKey());
        assertEquals("#00538a", rows.get(0).getColor());
        assertEquals(AggregationPeriod.MONTHLY, rows.get(0).getAggregation());
        assertEquals(ManipulationMode.NONE, rows.get(0).getManipulation());
        assertNull(rows.get(1).getAggregation());
        assertEquals(AnalysisChartType.LINE, w.getChartType());
    }

    @Test
    public void readsTheLegacyDataHandler() throws Exception {
        String widget = "{'uuid':4,'WidgetType':'Gauge','dataHandler':{'type':'SimpleDataHandler','forcedInterval':'LASTVALUE',"
                + "'data':[{'objectID':3001,'cleanObjectID':3002,'attribute':'Value','unit':'kWh','name':'Row C',"
                + "'aggregationPeriod':'DAILY','manipulationMode':'NONE','color':{'red':1.0,'green':0.0,'blue':0.0,'opacity':1.0}}]},"
                + "'gaugeDesign':{'minimum':0.0,'maximum':100.0,'inPercent':true,'showTitle':false,"
                + "'sections':[{'start':0.0,'end':50.0,'color':'0x00ff00ff'}]}}";
        WidgetDefinition w = new DashboardReader().readWidget(json(widget), null);
        assertEquals("LASTVALUE", w.getTimeFrame());
        RowDefinition row = w.getRows().get(0);
        assertEquals(3002L, row.getObjectId());
        assertEquals("#ff0000", row.getColor());
        assertEquals(AggregationPeriod.DAILY, row.getAggregation());
        assertEquals("kWh", row.getUnitDisplay());
        assertEquals(100d, w.getConfig().get("maximum"));
        assertEquals(true, w.getConfig().get("inPercent"));
        assertEquals(false, w.getConfig().get("showTitle"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) w.getConfig().get("sections");
        assertEquals("#00ff00", sections.get(0).get("color"));
    }

    @Test
    public void convertsTheLegacyDataHandlerToTheCurrentFormat() throws Exception {
        JsonNode handler = json("{'type':'SimpleDataHandler','forcedInterval':'LASTVALUE','data':[{'objectID':3001,"
                + "'cleanObjectID':3002,'attribute':'Value','unit':'kWh','name':'Row C','aggregationPeriod':'DAILY',"
                + "'manipulationMode':'NONE','enpi':true,'color':{'red':1.0,'green':0.0,'blue':0.0,'opacity':1.0}},"
                + "{'objectID':3003,'name':'Row D','color':'0x00538aff'}]}");
        com.fasterxml.jackson.databind.node.ObjectNode converted = new DashboardReader().convertLegacyDataHandler(handler, null);
        assertEquals("AnalysisHandler", converted.get("type").asText());
        assertEquals("LASTVALUE", converted.get("forcedInterval").asText());
        JsonNode rows = converted.get("chartModels").get(0).get("chartData");
        assertEquals(2, rows.size());
        JsonNode row = rows.get(0);
        assertEquals(3002L, row.get("id").asLong());
        assertEquals("Value", row.get("attributeString").asText());
        assertEquals("0xff0000ff", row.get("color").asText());
        assertEquals("DAILY", row.get("aggregationPeriod").asText());
        assertEquals("NONE", row.get("manipulationMode").asText());
        assertTrue(row.get("calculation").asBoolean());
        assertEquals("kWh", row.get("unitLabel").asText());
        assertEquals("0x00538aff", rows.get(1).get("color").asText());
        assertFalse(rows.get(1).has("unitLabel"));

        /* read back like the Control Center's next load: the same rows as the legacy handler */
        WidgetDefinition fromConverted = new DashboardReader().readWidget(json("{'uuid':4,'WidgetType':'Gauge'}"), null);
        assertTrue(fromConverted.getRows().isEmpty());
        com.fasterxml.jackson.databind.node.ObjectNode widget = MAPPER.createObjectNode();
        widget.put("uuid", 4).put("WidgetType", "Gauge").set("dataHandler", converted);
        RowDefinition read = new DashboardReader().readWidget(widget, null).getRows().get(0);
        assertEquals("#ff0000", read.getColor());
        assertEquals(AggregationPeriod.DAILY, read.getAggregation());
        assertEquals("kWh", read.getUnitDisplay());
    }

    @Test
    public void typeSpecificConfigurations() throws Exception {
        DashboardReader reader = new DashboardReader();
        WidgetDefinition timeFrame = reader.readWidget(json("{'uuid':5,'WidgetType':'Time Frame','Time Frame':{'format':'dd.MM.yyyy',"
                + "'selectedWidget':{'id':7,'start':'PERIODE_FROM','end':'LAST_TS','isCount':true}}}"), null);
        assertEquals("7", timeFrame.getConfig().get("source"));
        assertEquals("PERIOD_FROM", timeFrame.getConfig().get("start"));
        assertEquals("LAST_TS", timeFrame.getConfig().get("end"));
        assertEquals("dd.MM.yyyy", timeFrame.getConfig().get("format"));
        assertEquals(true, timeFrame.getConfig().get("count"));

        WidgetDefinition image = reader.readWidget(json("{'uuid':6,'WidgetType':'Image','image':{'objectID':'4001',"
                + "'dashboardLinker':{'dashboardObject':0},'analysisLinker':{'graphAnalysisObject':4002,'forcedInterval':'P1M'}}}"), null);
        assertEquals(4001L, image.getConfig().get("objectId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> link = (Map<String, Object>) image.getConfig().get("link");
        assertEquals("ANALYSIS", link.get("kind"));
        assertEquals(4002L, link.get("id"));
        assertEquals("P1M", link.get("timeFrame"));
        assertEquals(false, link.get("readable"));

        WidgetDefinition dashboardLink = reader.readWidget(json("{'uuid':9,'WidgetType':'Dashboard-Link','dashboardLinker':{'dashboardObject':5001}}"), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> dLink = (Map<String, Object>) dashboardLink.getConfig().get("link");
        assertEquals("DASHBOARD", dLink.get("kind"));
        assertNull(dLink.get("timeFrame"));

        WidgetDefinition alarm = reader.readWidget(json("{'uuid':10,'WidgetType':'Alarm Count','alarmCount':{'alarmObject':0}}"), null);
        assertTrue(alarm.getConfig().containsKey("objectId"));
        assertNull(alarm.getConfig().get("objectId"));

        WidgetDefinition sankey = reader.readWidget(json("{'uuid':11,'WidgetType':'Sankey','dataHandler':{'type':'AnalysisHandler',"
                + "'chartModels':[{'chartId':0,'chartData':[{'id':1,'attributeString':'Value'},{'id':2,'attributeString':'Value'}]}]},"
                + "'Sankey':{'showPercent':false,'sankeyDataRows':[{'id':1,'children':[2,99]}]}}"), null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) sankey.getConfig().get("nodes");
        assertEquals("0-1-Value", nodes.get(0).get("key"));
        assertEquals(1, ((List<?>) nodes.get(0).get("children")).size());
        assertEquals(false, sankey.getConfig().get("showPercent"));

        WidgetDefinition unknown = reader.readWidget(json("{'uuid':12,'WidgetType':'Future Widget','something':{'a':1}}"), null);
        assertEquals("Future Widget", unknown.getType());
        assertTrue(unknown.getConfig().isEmpty());
    }

    @Test
    public void namesAndColors() {
        assertEquals("CENTER_RIGHT", DashboardReader.titlePosition("right"));
        assertEquals("CENTER", DashboardReader.titlePosition("nowhere"));
        assertEquals("BOTTOM_LEFT", DashboardReader.titlePosition("BOTTOM_LEFT"));
        assertEquals("SEMI_BOLD", DashboardReader.fontWeight("Semi Bold"));
        assertEquals("NORMAL", DashboardReader.fontWeight(null));
        assertEquals("#00538a", WebColor.toCss("0x00538aff"));
        assertEquals("rgba(0,0,0,0)", WebColor.toCss("transparent"));
        assertEquals("rgba(16,32,48,0.5)", WebColor.toCss("rgba(16,32,48,0.5)"));
        assertEquals("#ffffff", WebColor.toCss("nonsense", "#ffffff"));
    }

    @Test
    public void emptyOrMissingFileGivesAnEmptyDashboard() throws Exception {
        DashboardDefinition d = new DashboardReader().readDashboardFile(null);
        assertTrue(d.getWidgets().isEmpty());
        assertEquals("P1D", d.getTimeFrame());
        assertEquals(1d, (Double) d.getZoomValue(), 0);
    }
}
