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
package org.jevis.commons.chart;

import org.jevis.api.*;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.unit.JEVisUnitImp;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Small Mockito based fakes of the JEVis API for the chart tests. Neutral names only.
 */
final class ChartTestData {
    static final DateTimeZone BERLIN = DateTimeZone.forID("Europe/Berlin");

    private ChartTestData() {
    }

    static JEVisDataSource dataSource() {
        return mock(JEVisDataSource.class);
    }

    /**
     * A data object with a "Value" attribute that answers every aggregated sample request with the given samples.
     */
    static DataFake data(JEVisDataSource ds, long id, String name, String className, JEVisUnit displayUnit, List<JEVisSample> samples) throws JEVisException {
        DataFake fake = new DataFake();
        fake.object = mock(JEVisObject.class);
        fake.value = mock(JEVisAttribute.class);
        when(fake.object.getID()).thenReturn(id);
        when(fake.object.getName()).thenReturn(name);
        when(fake.object.getJEVisClassName()).thenReturn(className);
        when(fake.object.getDataSource()).thenReturn(ds);
        when(fake.object.getParents()).thenReturn(new ArrayList<>());
        when(fake.object.getAttribute("Value")).thenReturn(fake.value);
        when(fake.value.getName()).thenReturn("Value");
        when(fake.value.getObject()).thenReturn(fake.object);
        when(fake.value.getDisplayUnit()).thenReturn(displayUnit);
        when(fake.value.getSamples(any(DateTime.class), any(DateTime.class), anyBoolean(), anyString(), anyString(), anyString()))
                .thenReturn(samples);
        when(ds.getObject(id)).thenReturn(fake.object);
        return fake;
    }

    /**
     * Adds a "Period" attribute (raw sample period) to the object.
     */
    static void period(JEVisObject object, String period) throws JEVisException {
        JEVisAttribute periodAttribute = mock(JEVisAttribute.class);
        VirtualSample sample = new VirtualSample(new DateTime(2020, 1, 1, 0, 0, DateTimeZone.UTC), period);
        when(periodAttribute.getAllSamples()).thenReturn(Collections.singletonList(sample));
        when(periodAttribute.getLatestSample()).thenReturn(sample);
        when(periodAttribute.hasSample()).thenReturn(true);
        when(object.getAttribute("Period")).thenReturn(periodAttribute);
    }

    static JEVisUnit unit(String label) {
        return new JEVisUnitImp(label, label, "");
    }

    static List<JEVisSample> samples(DateTime first, int minutesStep, double... values) {
        List<JEVisSample> list = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            VirtualSample sample = new VirtualSample(first.plusMinutes(i * minutesStep), values[i]);
            sample.setNote("note" + i);
            list.add(sample);
        }
        return list;
    }

    static RowDefinition row(String key, long objectId, String unitLabel) {
        RowDefinition row = new RowDefinition();
        row.setKey(key);
        row.setObjectId(objectId);
        row.setAttribute("Value");
        row.setName("Row " + objectId);
        row.setUnitFormula(unitLabel);
        row.setUnitLabel(unitLabel);
        row.setUnitPrefix("");
        return row;
    }

    static EvaluationSettings settings() {
        return new EvaluationSettings().setZone(BERLIN);
    }

    static TimeRange range(DateTime start, DateTime end) {
        return new TimeRange(start, end);
    }

    static final class DataFake {
        JEVisObject object;
        JEVisAttribute value;
    }
}
