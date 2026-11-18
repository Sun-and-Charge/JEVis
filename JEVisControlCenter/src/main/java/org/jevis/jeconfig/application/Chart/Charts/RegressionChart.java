/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEConfig.
 * <p>
 * JEConfig is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
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
package org.jevis.jeconfig.application.Chart.Charts;

import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisSample;
import org.jevis.commons.chart.BubbleType;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.regression.MultipleRegression;
import org.jevis.jeconfig.application.Chart.ChartType;
import org.jevis.jeconfig.application.Chart.data.ChartDataRow;
import org.jevis.jeconfig.application.Chart.data.ChartModel;
import org.jevis.jeconfig.plugin.charts.DataSettings;
import org.jevis.jeconfig.plugin.charts.ToolBarSettings;
import org.joda.time.DateTime;

import java.text.NumberFormat;
import java.util.*;

/**
 * Chart type REGRESSION (multivariate regression, as in the web app): the row marked Y (else the first row) is
 * explained by the rows marked X (else all other rows) with least squares over the common timestamps. The chart shows
 * the measured values of the target and the model; equation and quality figures are added to the title.
 */
public class RegressionChart extends LineChart {
    private static final Logger logger = LogManager.getLogger(RegressionChart.class);
    private String summary = "";

    public RegressionChart(JEVisDataSource ds, ChartModel chartModel) {
        super(ds, chartModel);
    }

    @Override
    public void buildChart(ToolBarSettings toolBarSettings, DataSettings dataSettings) {
        try {
            if (chartDataRows.isEmpty()) {
                chartModel.getChartData().forEach(chartData -> {
                    ChartDataRow row = new ChartDataRow(ds, chartData);
                    row.setCustomWorkDay(chartModel.isCustomWorkDay());
                    chartDataRows.add(row);
                });
            }
            List<ChartDataRow> rows = new ArrayList<>(chartDataRows);
            ChartDataRow target = null;
            for (ChartDataRow row : rows) if (row.getBubbleType() == BubbleType.Y) target = row;
            if (target == null) for (ChartDataRow row : rows) if (row.getBubbleType() != BubbleType.X) {
                target = row;
                break;
            }
            if (target == null) {
                summary = text("plugin.graph.regression.notarget", "Keine Zielgröße: eine Datenreihe mit Werten als Zielgröße (Blasentyp Y) markieren", "No target: mark a data row with values as the target (bubble type Y)");
                super.buildChart(toolBarSettings, dataSettings);
                return;
            }
            List<ChartDataRow> drivers = new ArrayList<>();
            for (ChartDataRow row : rows) if (row != target && row.getBubbleType() == BubbleType.X) drivers.add(row);
            if (drivers.isEmpty()) for (ChartDataRow row : rows) if (row != target && row.getBubbleType() != BubbleType.SIZE) drivers.add(row);

            SortedMap<Long, Double> y = values(target);
            List<Map<Long, Double>> xs = new ArrayList<>();
            for (ChartDataRow driver : drivers) xs.add(values(driver));
            MultipleRegression regression = MultipleRegression.fit(y, xs);

            target.setChartType(ChartType.LINE);
            List<ChartDataRow> shown = new ArrayList<>(Collections.singletonList(target));
            if (regression.getProblem() == null) {
                ChartDataRow model = target.clone();
                List<JEVisSample> modelled = new ArrayList<>();
                for (int i = 0; i < regression.getN(); i++) {
                    modelled.add(new VirtualSample(new DateTime(regression.getTimestamps()[i]), regression.getModelled()[i]));
                }
                model.setSamples(modelled);
                model.setSomethingChanged(false);
                model.setName(text("plugin.graph.regression.model", "Modell", "Model") + ": " + target.getName());
                model.setColor(Color.web("#1a2a45"));
                model.setChartType(ChartType.LINE);
                shown.add(model);
                summary = summary(regression, target, drivers);
            } else {
                summary = problemText(regression.getProblem());
            }
            chartDataRows.clear();
            chartDataRows.addAll(shown);
        } catch (Exception e) {
            logger.error("Could not compute the regression of chart {}", chartModel.getChartName(), e);
        }
        super.buildChart(toolBarSettings, dataSettings);
    }

    private static SortedMap<Long, Double> values(ChartDataRow row) {
        SortedMap<Long, Double> map = new TreeMap<>();
        for (JEVisSample sample : row.getSamples()) {
            try {
                map.put(sample.getTimestamp().getMillis(), sample.getValueAsDouble());
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    private static String summary(MultipleRegression r, ChartDataRow target, List<ChartDataRow> drivers) {
        NumberFormat nf = NumberFormat.getNumberInstance(I18n.getInstance().getLocale());
        nf.setMaximumFractionDigits(4);
        StringBuilder sb = new StringBuilder();
        sb.append(target.getName()).append(" = ").append(nf.format(r.getCoefficients()[0]));
        for (int j = 0; j < drivers.size(); j++) {
            double c = r.getCoefficients()[j + 1];
            sb.append(c < 0 ? " − " : " + ").append(nf.format(Math.abs(c))).append(" · ").append(drivers.get(j).getName());
        }
        NumberFormat q = NumberFormat.getNumberInstance(I18n.getInstance().getLocale());
        q.setMinimumFractionDigits(3);
        q.setMaximumFractionDigits(3);
        sb.append("   (R² ").append(q.format(r.getRSquared()))
                .append(", ").append(text("plugin.graph.regression.adjusted", "bereinigt", "adjusted")).append(" ").append(q.format(r.getAdjustedRSquared()))
                .append(", n ").append(r.getN());
        if (r.getCvRmse() != null) {
            NumberFormat pct = NumberFormat.getPercentInstance(I18n.getInstance().getLocale());
            pct.setMaximumFractionDigits(1);
            sb.append(", CV(RMSE) ").append(pct.format(r.getCvRmse()));
        }
        sb.append(")");
        return sb.toString();
    }

    private static String problemText(MultipleRegression.Problem problem) {
        switch (problem) {
            case NO_DRIVERS:
                return text("plugin.graph.regression.problem.no_drivers", "Keine Einflussgröße: die Regression braucht mindestens eine weitere Datenreihe",
                        "No driver: the regression needs at least one more data row");
            case TOO_FEW_POINTS:
                return text("plugin.graph.regression.problem.too_few_points", "Zu wenige gemeinsame Zeitpunkte: längeren Zeitraum oder gleiche Aggregation wählen",
                        "Too few common timestamps: choose a longer period or the same aggregation");
            default:
                return text("plugin.graph.regression.problem.collinear", "Die Einflussgrößen hängen linear voneinander ab; eine davon entfernen",
                        "The drivers depend linearly on each other; remove one of them");
        }
    }

    /**
     * The translation of the key, or the German/English default while the key is not in the language files yet
     * (I18n answers a missing key as {@code *key*}).
     */
    public static String text(String key, String german, String english) {
        String value = I18n.getInstance().getString(key);
        if (value != null && !value.startsWith("*")) return value;
        return "de".equals(I18n.getInstance().getLocale().getLanguage()) ? german : english;
    }

    @Override
    String getUpdatedChartName() {
        String name = super.getUpdatedChartName();
        return summary.isEmpty() ? name : name + " – " + summary;
    }
}
