/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.sample.csvexporttable;

import javafx.beans.property.SimpleStringProperty;
import org.jevis.api.JEVisSample;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Old TableView for JEVisSamples, used by CSV Export Plugin.
 *
 * @author Benjamin Reich
 * @deprecated replaced by CSVExportTableSampleTable
 */
public class CSVExportTableSample {


    static final DateTimeFormatter fmtDate = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss Z");

    private final SimpleStringProperty _date = new SimpleStringProperty("ERROR");
    private final SimpleStringProperty _value = new SimpleStringProperty("ERROR");
    private final SimpleStringProperty _note = new SimpleStringProperty("ERROR");
    private JEVisSample _sample;

    public CSVExportTableSample(JEVisSample sample) {
        try {
            _date.set(fmtDate.print(sample.getTimestamp()));
        } catch (Exception ex) {

        }
        try {
            _value.set(sample.getValueAsString());
        } catch (Exception ex) {

        }
        try {
            _note.set(sample.getNote());
        } catch (Exception ex) {

        }
        _sample = sample;
    }

    public String getDate() {
        return _date.get();
    }

    public void setDate(String date) {
        _date.set(date);
    }

    public SimpleStringProperty dateProperty() {
        return _date;
    }

    public String getValue() {
        return _value.get();
    }

    public void setValue(String value) {
        _value.set(value);
    }

    public SimpleStringProperty valueProperty() {
        return _value;
    }

    public String getNote() {
        return _note.get();
    }

    public void setNote(String note) {
        _note.set(note);
    }

    public SimpleStringProperty getPrimaryEmailProperty() {
        return _note;
    }

    public JEVisSample getSample() {
        return _sample;
    }

    public void setSample(JEVisSample sample) {
        _sample = sample;
    }
}
