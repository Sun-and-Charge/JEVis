/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEConfig.
 * <p>
 * JEConfig is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEConfig is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEConfig. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEConfig is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.application.Chart.data;

import com.fasterxml.jackson.databind.JsonNode;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.junit.Test;

import static org.junit.Assert.*;

public class AnalysisHandlerTest {

    private static DataModel modelWithAggregatedRow() {
        ChartData row = new ChartData();
        row.setId(2001);
        row.setAttributeString("Value");
        row.setAggregationPeriod(AggregationPeriod.DAILY);
        row.setManipulationMode(ManipulationMode.SUM);
        ChartModel chart = new ChartModel();
        chart.getChartData().add(row);
        DataModel model = new DataModel();
        model.getChartModels().add(chart);
        return model;
    }

    private static JsonNode firstRow(JsonNode node) {
        return node.get("chartModels").get(0).get("chartData").get(0);
    }

    @Test
    public void analysesDoNotSaveRowAggregation() {
        JsonNode row = firstRow(new AnalysisHandler().toJsonNode(modelWithAggregatedRow()));
        assertFalse(row.has("aggregationPeriod"));
        assertFalse(row.has("manipulationMode"));
    }

    @Test
    public void dashboardWidgetsKeepRowAggregation() {
        AnalysisHandler handler = new AnalysisHandler();
        JsonNode node = handler.toJsonNode(modelWithAggregatedRow(), true);
        assertEquals("DAILY", firstRow(node).get("aggregationPeriod").asText());
        assertEquals("SUM", firstRow(node).get("manipulationMode").asText());

        DataModel reloaded = new DataModel();
        handler.jsonToModel(node, reloaded);
        ChartData row = reloaded.getChartModels().get(0).getChartData().get(0);
        assertEquals(AggregationPeriod.DAILY, row.getAggregationPeriod());
        assertEquals(ManipulationMode.SUM, row.getManipulationMode());
    }
}
