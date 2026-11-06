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

import org.jevis.commons.chart.RowResult;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The values of a dashboard for a time frame: the resolved interval, the reference dates of the previous and next
 * period and the data of the widgets (by uuid).
 */
public class DashboardResult {
    private String timeFrame;
    private DateTime from;
    private DateTime to;
    private DateTime previous;
    private DateTime next;
    private final Map<String, WidgetResult> widgets = new LinkedHashMap<>();

    /**
     * @return the time frame that was evaluated (after fallbacks)
     */
    public String getTimeFrame() {
        return timeFrame;
    }

    public void setTimeFrame(String timeFrame) {
        this.timeFrame = timeFrame;
    }

    public DateTime getFrom() {
        return from;
    }

    public void setFrom(DateTime from) {
        this.from = from;
    }

    public DateTime getTo() {
        return to;
    }

    public void setTo(DateTime to) {
        this.to = to;
    }

    public DateTime getPrevious() {
        return previous;
    }

    public void setPrevious(DateTime previous) {
        this.previous = previous;
    }

    public DateTime getNext() {
        return next;
    }

    public void setNext(DateTime next) {
        this.next = next;
    }

    public Map<String, WidgetResult> getWidgets() {
        return widgets;
    }

    /**
     * The data of one widget.
     */
    public static class WidgetResult {
        /**
         * How the rows were evaluated (decides which fields the web service writes).
         */
        public enum Kind {
            /**
             * samples and statistics per row (Chart widgets)
             */
            SERIES,
            /**
             * one value per row (absolute mode)
             */
            VALUE,
            /**
             * the latest text per row (String Value)
             */
            TEXT,
            /**
             * the latest value of the attribute (input widgets)
             */
            LATEST,
            /**
             * number of alarms (Alarm Count)
             */
            ALARMS
        }

        private final Kind kind;
        private DateTime from;
        private DateTime to;
        private final List<RowResult> rows = new ArrayList<>();
        private Integer alarmCount;

        public WidgetResult(Kind kind) {
            this.kind = kind;
        }

        public Kind getKind() {
            return kind;
        }

        public DateTime getFrom() {
            return from;
        }

        public void setFrom(DateTime from) {
            this.from = from;
        }

        public DateTime getTo() {
            return to;
        }

        public void setTo(DateTime to) {
            this.to = to;
        }

        public List<RowResult> getRows() {
            return rows;
        }

        public Integer getAlarmCount() {
            return alarmCount;
        }

        public void setAlarmCount(Integer alarmCount) {
            this.alarmCount = alarmCount;
        }
    }
}
