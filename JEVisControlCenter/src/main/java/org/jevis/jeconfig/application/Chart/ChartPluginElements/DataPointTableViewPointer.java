/**
 * Copyright (c) 2016 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 * <p>
 * Based on de.gsi.chart.plugins.DataPointTooltip of chartfx 8.1.5 (https://github.com/GSI-CS-CO/chart-fx),
 * licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
 * <p>
 * Modified for JEVis (data notes, alarms, table view of the selected data point).
 * The modifications are Copyright (C) JEVis contributors and Copyright (C) 2026 Sun and Charge Germany GmbH,
 * licensed under the GNU General Public License version 3, see <https://github.com/Sun-and-Charge/JEVis>.
 */

package org.jevis.jeconfig.application.Chart.ChartPluginElements;

import javafx.scene.control.ComboBox;
import de.gsi.chart.Chart;
import de.gsi.chart.XYChart;
import de.gsi.chart.axes.Axis;
import de.gsi.chart.plugins.AbstractDataFormattingPlugin;
import de.gsi.dataset.DataSet;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseEvent;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisSample;
import org.jevis.commons.alarm.Alarm;
import org.jevis.commons.datetime.WorkDays;
import org.jevis.jeconfig.application.Chart.ChartElements.Note;
import org.jevis.jeconfig.application.Chart.ChartElements.TableEntry;
import org.jevis.jeconfig.application.Chart.ChartElements.XYChartSerie;
import org.jevis.jeconfig.application.Chart.ChartType;
import org.jevis.jeconfig.application.Chart.Charts.BubbleChart;
import org.jevis.jeconfig.application.Chart.Charts.LogicalChart;
import org.jevis.jeconfig.application.Chart.Charts.TableChart;
import org.jevis.jeconfig.application.Chart.data.ChartDataRow;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

public class DataPointTableViewPointer extends AbstractDataFormattingPlugin {

    private static final Logger logger = LogManager.getLogger(DataPointTableViewPointer.class);
    private final org.jevis.jeconfig.application.Chart.Charts.XYChart currentChart;
    private final List<org.jevis.jeconfig.application.Chart.Charts.Chart> notActiveCharts;
    private final List<XYChartSerie> xyChartSerieList;
    boolean plotArea = true;
    private DateTime timestampOfFirstSample = null;
    private boolean asDuration = false;
    private WorkDays workDays;
    /* shared crosshair (J25.5): a vertical line in every chart of the analysis, the values of all rows in the hovered one */
    private final javafx.scene.shape.Line crosshair = new javafx.scene.shape.Line();
    private final javafx.scene.layout.VBox crosshairTip = new javafx.scene.layout.VBox();

    public DataPointTableViewPointer(org.jevis.jeconfig.application.Chart.Charts.Chart chart, List<org.jevis.jeconfig.application.Chart.Charts.Chart> notActive) {
        super();
        this.currentChart = (org.jevis.jeconfig.application.Chart.Charts.XYChart) chart;
        this.notActiveCharts = notActive;
        this.asDuration = this.currentChart.isAsDuration();
        this.xyChartSerieList = this.currentChart.getXyChartSerieList();

        for (ChartDataRow chartDataRow : this.currentChart.getChartDataRows()) {
            workDays = new WorkDays(chartDataRow.getObject());
            break;
        }

        this.timestampOfFirstSample = this.currentChart.getTimeStampOfFirstSample().get();

        EventHandler<MouseEvent> mouseMoveHandler = event -> {
            try {
                plotArea = true;
                updateTable(event);
            } catch (Exception e) {
                logger.error("Error on mouse move handler", e);
            }
        };
        registerInputEventHandler(MouseEvent.MOUSE_MOVED, mouseMoveHandler);
        this.currentChart.getChart().addEventHandler(MouseEvent.MOUSE_EXITED, event -> hideCrosshairs());

        crosshair.getStyleClass().add("suc-crosshair");
        crosshair.setMouseTransparent(true);
        crosshair.setVisible(false);
        crosshairTip.getStyleClass().add("suc-crosshair-tip");
        crosshairTip.setMouseTransparent(true);
        crosshairTip.setVisible(false);
        crosshairTip.setManaged(false);
        getChartChildren().addAll(crosshair, crosshairTip);

        this.currentChart.getChart().setOnMouseMoved(event -> {
            try {
                plotArea = false;
                updateTable(event);
            } catch (Exception e) {
                logger.error("Error on mouse moved handler", e);
            }
        });
    }

    private DataPoint findNearestDataPointWithinPickingDistance(final Chart chart, final Point2D mouseLocation, Axis axis) {
        DataPoint nearestDataPoint = null;
        if (!(chart instanceof XYChart)) {
            return null;
        }

        final XYChart xyChart = (XYChart) chart;
        Axis xAxis;
        if (axis != null) {
            xAxis = axis;
        } else {
            xAxis = xyChart.getXAxis();
        }

        final double xValue = xAxis.getValueForDisplay(mouseLocation.getX());

        for (final DataPointTableViewPointer.DataPoint dataPoint : findNeighborPoints(xyChart, xValue)) {
            if (getChart().getFirstAxis(Orientation.HORIZONTAL) != null) {
                final double x = xAxis.getDisplayPosition(dataPoint.x);
                final double y = xyChart.getYAxis().getDisplayPosition(dataPoint.y);
                final Point2D displayPoint = new Point2D(x, y);
                /* nearest point in time only (J25.5), so the table shows the same time as the crosshair tooltip */
                dataPoint.distanceFromMouse = Math.abs(displayPoint.getX() - mouseLocation.getX());
                if (dataPoint.distanceFromMouse <= 10000 && (nearestDataPoint == null
                        || dataPoint.distanceFromMouse < nearestDataPoint.distanceFromMouse)) {
                    nearestDataPoint = dataPoint;
                }
            }
        }
        return nearestDataPoint;
    }

    /**
     * Handles series that have data sorted or not sorted with respect to X coordinate.
     *
     * @param dataSet   data set
     * @param searchedX x coordinate
     * @return return neighouring data points
     */
    private Pair<DataPoint, DataPoint> findNeighborPoints(final DataSet dataSet, final double searchedX) {
        int prevIndex = -1;
        int nextIndex = -1;
        double prevX = Double.MIN_VALUE;
        double nextX = Double.MAX_VALUE;

        final int nDataCount = dataSet.getDataCount();
        for (int i = 0; i < nDataCount; i++) {
            final double currentX = dataSet.get(DataSet.DIM_X, i);

            if (currentX < searchedX) {
                if (prevX < currentX) {
                    prevIndex = i;
                    prevX = currentX;
                }
            } else if (nextX > currentX) {
                nextIndex = i;
                nextX = currentX;
            }
        }
        final DataPoint prevPoint = prevIndex == -1 ? null
                : new DataPoint(getChart(), dataSet.get(DataSet.DIM_X, prevIndex),
                dataSet.get(DataSet.DIM_Y, prevIndex), getDataLabelSafe(dataSet, prevIndex));
        final DataPoint nextPoint = nextIndex == -1 || nextIndex == prevIndex ? null
                : new DataPoint(getChart(), dataSet.get(DataSet.DIM_X, nextIndex),
                dataSet.get(DataSet.DIM_X, nextIndex), getDataLabelSafe(dataSet, nextIndex));

        return new Pair<>(prevPoint, nextPoint);
    }

    private List<DataPoint> findNeighborPoints(final XYChart chart, final double searchedX) {
        final List<DataPoint> points = new LinkedList<>();
        for (final DataSet dataSet : chart.getAllDatasets()) {
            final Pair<DataPoint, DataPoint> neighborPoints = findNeighborPoints(dataSet, searchedX);
            if (neighborPoints.getKey() != null) {
                points.add(neighborPoints.getKey());
            }
            if (neighborPoints.getValue() != null) {
                points.add(neighborPoints.getValue());
            }
        }
        return points;
    }

    protected String getDataLabelSafe(final DataSet dataSet, final int index) {
        String lable = dataSet.getDataLabel(index);
        if (lable == null) {
            return getDefaultDataLabel(dataSet, index);
        }
        return lable;
    }

    /**
     * Returns label of a data point specified by the index. The label can be used as a category name if
     * CategoryStepsDefinition is used or for annotations displayed for data points.
     *
     * @param index data point index
     * @return label of a data point specified by the index or <code>null</code> if none label has been specified for
     * this data point.
     */
    protected String getDefaultDataLabel(final DataSet dataSet, final int index) {
        return String.format("%s (%d, %s, %s)", dataSet.getName(), index,
                dataSet.get(DataSet.DIM_X, index), dataSet.get(DataSet.DIM_Y, index));
    }


    private void updateTable(final MouseEvent event) {
        try {
            Bounds areaBounds = null;
            if (plotArea)
                areaBounds = getChart().getPlotArea().getBoundsInLocal();
            else if (getChart() != null) {
                areaBounds = getChart().getBoundsInLocal();
            }

            if (areaBounds == null || !areaBounds.contains(event.getX(), event.getY())) {
                return;
            }

            final Point2D mouseLocation = getLocationInPlotArea(event);

            if (mouseLocation != null && currentChart.getChartType() != ChartType.BUBBLE) {

                updateTable(mouseLocation);

                double xValue = getChart() instanceof XYChart ? ((XYChart) getChart()).getXAxis().getValueForDisplay(mouseLocation.getX()) : Double.NaN;
                /* only the plot area handler moves the crosshair; the chart handler (plotArea false) fires as well
                   for the same mouse move and must not hide it again */
                if (plotArea && !Double.isNaN(xValue)) {
                    showCrosshair(xValue, mouseLocation);
                }

                if (!notActiveCharts.isEmpty()) {
                    for (org.jevis.jeconfig.application.Chart.Charts.Chart chart : notActiveCharts) {
                        if (chart.getChart() != null && !chart.getChartType().equals(ChartType.PIE)
                                && !chart.getChartType().equals(ChartType.BAR)
                                && !chart.getChartType().equals(ChartType.TABLE)) {
                            chart.getChart().getPlugins().forEach(chartPlugin -> {
                                if (chartPlugin instanceof DataPointTableViewPointer) {
                                    DataPointTableViewPointer other = (DataPointTableViewPointer) chartPlugin;
                                    other.updateTable(mouseLocation);
                                    if (plotArea && !Double.isNaN(xValue)) other.showCrosshair(xValue, null);
                                }
                            });
                        } else if (chart.getChartType().equals(ChartType.TABLE)) {
                            final DataPoint dataPoint = findNearestDataPointWithinPickingDistance(getChart(), mouseLocation, null);
                            if (dataPoint == null) continue;

                            Double v = dataPoint.getX() * 1000d;
                            DateTime nearest = new DateTime(v.longValue());

                            TableChart tableChart = (TableChart) chart;
                            tableChart.updateTable(null, nearest);
                            tableChart.setBlockDatePickerEvent(true);
                            TableTopDatePicker tableTopDatePicker = tableChart.getTableTopDatePicker();
                            ComboBox<DateTime> datePicker = tableTopDatePicker.getDatePicker();
                            Platform.runLater(() -> {
                                datePicker.getSelectionModel().select(nearest);
                                tableChart.setBlockDatePickerEvent(false);
                            });
                        }
                    }
                }
            } else {
                final DataPoint dataPoint = findNearestDataPointWithinPickingDistance(getChart(), mouseLocation, null);
                if (dataPoint != null) {
                    Double v = dataPoint.getX();
                    updateTable(v);
                }
            }
        } catch (Exception e) {
            logger.error("Error while updating table", e);
        }
    }

    /**
     * Shows the vertical crosshair line at the given x value (seconds); with a mouse location also the tooltip with the
     * time and the values of all rows of this chart next to the mouse.
     */
    public void showCrosshair(double xValue, Point2D mouseLocation) {
        if (!(getChart() instanceof XYChart)) return;
        XYChart xyChart = (XYChart) getChart();
        double x = xyChart.getXAxis().getDisplayPosition(xValue);
        Bounds plot = xyChart.getPlotArea().getBoundsInLocal();
        if (Double.isNaN(x) || x < 0 || x > plot.getWidth()) {
            hideCrosshair();
            return;
        }
        crosshair.setStartX(x);
        crosshair.setEndX(x);
        crosshair.setStartY(0);
        crosshair.setEndY(plot.getHeight());
        crosshair.setVisible(true);
        crosshair.toFront();

        if (mouseLocation == null || xyChartSerieList.isEmpty()) {
            crosshairTip.setVisible(false);
            return;
        }

        DateTime at = new DateTime((long) (xValue * 1000d));
        List<javafx.scene.Node> lines = new LinkedList<>();
        DateTime shownTime = null;
        for (XYChartSerie serie : xyChartSerieList) {
            try {
                if (!serie.isShownInRenderer()) continue;
                TreeMap<DateTime, JEVisSample> samples = serie.getSampleMap();
                if (samples == null || samples.isEmpty()) continue;
                Map.Entry<DateTime, JEVisSample> floor = samples.floorEntry(at);
                Map.Entry<DateTime, JEVisSample> ceiling = samples.ceilingEntry(at);
                Map.Entry<DateTime, JEVisSample> nearest = floor == null ? ceiling : ceiling == null ? floor
                        : (at.getMillis() - floor.getKey().getMillis() <= ceiling.getKey().getMillis() - at.getMillis() ? floor : ceiling);
                if (nearest == null) continue;
                if (shownTime == null) shownTime = nearest.getKey();

                String value = serie.getSingleRow().isStringData()
                        ? nearest.getValue().getValueAsString()
                        : serie.getNf().format(nearest.getValue().getValueAsDouble());

                javafx.scene.layout.Region marker = new javafx.scene.layout.Region();
                marker.setMinSize(10, 3);
                marker.setMaxSize(10, 3);
                marker.setStyle("-fx-background-radius: 2; -fx-background-color: " + toWeb(serie.getTableEntry().getColor()) + ";");
                javafx.scene.control.Label name = new javafx.scene.control.Label(serie.getTableEntryName());
                javafx.scene.control.Label val = new javafx.scene.control.Label(value + " " + serie.getUnit());
                val.getStyleClass().add("suc-crosshair-value");
                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                javafx.scene.layout.HBox line = new javafx.scene.layout.HBox(6, marker, name, spacer, val);
                line.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                lines.add(line);
            } catch (Exception e) {
                logger.debug("No crosshair value for {}", serie.getTableEntryName(), e);
            }
        }
        if (lines.isEmpty()) {
            crosshairTip.setVisible(false);
            return;
        }

        /* the row format (PeriodHelper), but for minute and hour values shorter than its ISO pattern: "Do. 19.11.2026 09:15" */
        String pattern = xyChartSerieList.get(0).getSingleRow().getFormatString();
        if ("yyyy-MM-dd HH:mm:ss".equals(pattern)) pattern = "EE dd.MM.yyyy HH:mm";
        javafx.scene.control.Label time = new javafx.scene.control.Label(shownTime == null ? ""
                : org.joda.time.format.DateTimeFormat.forPattern(pattern).withLocale(org.jevis.commons.i18n.I18n.getInstance().getLocale()).print(shownTime));
        time.getStyleClass().add("suc-crosshair-time");
        crosshairTip.getChildren().setAll(time);
        crosshairTip.getChildren().addAll(lines);
        /* the tip is unmanaged: size and lay out its new rows now, otherwise they stay empty until another layout pass */
        crosshairTip.applyCss();
        crosshairTip.autosize();
        crosshairTip.layout();

        /* right of the mouse, or left of it near the right edge; kept inside the plot area */
        double w = crosshairTip.getWidth();
        double h = crosshairTip.getHeight();
        double tx = x + 14 + w > plot.getWidth() ? x - 14 - w : x + 14;
        double ty = Math.min(Math.max(0, mouseLocation.getY() - h / 2), Math.max(0, plot.getHeight() - h));
        crosshairTip.relocate(Math.max(0, tx), ty);
        crosshairTip.setVisible(true);
        crosshairTip.toFront();
    }

    public void hideCrosshair() {
        crosshair.setVisible(false);
        crosshairTip.setVisible(false);
    }

    /* hides the crosshair in this and all linked charts when the mouse leaves the plot */
    private void hideCrosshairs() {
        hideCrosshair();
        for (org.jevis.jeconfig.application.Chart.Charts.Chart chart : notActiveCharts) {
            if (chart.getChart() == null) continue;
            chart.getChart().getPlugins().forEach(chartPlugin -> {
                if (chartPlugin instanceof DataPointTableViewPointer) {
                    ((DataPointTableViewPointer) chartPlugin).hideCrosshair();
                }
            });
        }
    }

    private static String toWeb(javafx.scene.paint.Color c) {
        if (c == null) return "#888888";
        return String.format("#%02x%02x%02x", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    public void updateTable(final Point2D mouseLocation) {

        Period period = this.currentChart.getPeriod();

        for (XYChartSerie xyChartSerie : xyChartSerieList) {
            try {
                final DataPoint dataPoint = findNearestDataPointWithinPickingDistance(getChart(), mouseLocation, xyChartSerie.getXAxis());

                if (dataPoint == null) continue;

                Double v = dataPoint.getX() * 1000d;
                DateTime nearest = new DateTime(v.longValue());

                TableEntry tableEntry = xyChartSerie.getTableEntry();
                TreeMap<DateTime, JEVisSample> sampleTreeMap = xyChartSerie.getSampleMap();
                Map<DateTime, JEVisSample> noteMap = xyChartSerie.getSingleRow().getNoteSamples();
                Map<DateTime, Alarm> alarmMap = xyChartSerie.getSingleRow().getAlarms(false);

                DateTime dateTime = nearest;
                if (currentChart instanceof LogicalChart) {
                    dateTime = sampleTreeMap.lowerKey(nearest);
                }

                JEVisSample sample = sampleTreeMap.get(dateTime);

                Note formattedNote = new Note(sample, noteMap.get(sample.getTimestamp()), alarmMap.get(sample.getTimestamp()));

                if (workDays != null && period != null && workDays.getWorkdayEnd().isBefore(workDays.getWorkdayStart())
                        && (period.getDays() > 0
                        || period.getWeeks() > 0
                        || period.getMonths() > 0
                        || period.getYears() > 0)) {
                    dateTime = dateTime.plusDays(1);
                }

                DateTime finalDateTime = dateTime;

                if (!asDuration) {
                    Platform.runLater(() -> tableEntry.setDate(finalDateTime
                            .toString(xyChartSerie.getSingleRow().getFormatString())));
                } else {
                    Platform.runLater(() -> tableEntry.setDate((finalDateTime.getMillis() -
                            timestampOfFirstSample.getMillis()) / 1000 / 60 / 60 + " h"));
                }
                Platform.runLater(() -> tableEntry.setNote(formattedNote.getNoteAsString()));
                String unit = xyChartSerie.getUnit();

                if (!sample.getNote().contains("Zeros")) {
                    Double valueAsDouble = null;
                    String formattedDouble = null;

                    if (!xyChartSerie.getSingleRow().isStringData()) {
                        try {
                            valueAsDouble = sample.getValueAsDouble();
                        } catch (JEVisException e) {
                            e.printStackTrace();
                        }

                        formattedDouble = xyChartSerie.getNf().format(valueAsDouble);
                        String finalFormattedDouble = formattedDouble;
                        Platform.runLater(() -> tableEntry.setValue(finalFormattedDouble + " " + unit));
                    } else {
                        Platform.runLater(() -> {
                            try {
                                tableEntry.setValue(sample.getValueAsString() + " " + unit);
                            } catch (JEVisException e) {
                                e.printStackTrace();
                            }
                        });
                    }

                } else Platform.runLater(() -> tableEntry.setValue("- " + unit));

            } catch (Exception ignored) {
            }
        }
    }

    public void updateTable(Double nearest) {

        try {
            BubbleChart currentChart = (BubbleChart) this.currentChart;

            TreeMap<Double, Double> sampleTreeMap = currentChart.getSampleTreeMap();
            TableEntry tableEntry = currentChart.getTableData().get(0);

            Double yValue = sampleTreeMap.get(nearest);
            AtomicReference<Double> xValue = new AtomicReference<>(nearest);
            sampleTreeMap.forEach((aDouble, aDouble2) -> {
                if (aDouble2.equals(yValue)) {
                    xValue.set(aDouble);
                }
            });

            String formattedX = currentChart.getNf().format(xValue.get());
            String formattedY = currentChart.getNf().format(yValue);
            String xUnit = currentChart.getxUnit();
            String yUnit = currentChart.getyUnit();
            if (!xUnit.equals("")) {
                Platform.runLater(() -> tableEntry.setxValue(formattedX + " " + xUnit));
            } else {
                Platform.runLater(() -> tableEntry.setxValue(formattedX));
            }
            if (!yUnit.equals("")) {
                Platform.runLater(() -> tableEntry.setyValue(formattedY + " " + yUnit));
            } else {
                Platform.runLater(() -> tableEntry.setyValue(formattedY));
            }

        } catch (Exception ex) {
        }
    }

    protected static class DataPoint {

        protected final Chart chart;
        protected final double x;
        protected final double y;
        protected final String label;
        protected double distanceFromMouse;

        protected DataPoint(final Chart chart, final double x, final double y, final String label) {
            this.chart = chart;
            this.x = x;
            this.y = y;
            this.label = label;
        }

        public Chart getChart() {
            return chart;
        }

        public double getDistanceFromMouse() {
            return distanceFromMouse;
        }

        public String getLabel() {
            return label;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

    }
}
