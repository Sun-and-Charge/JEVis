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
package org.jevis.jeconfig.plugin.loadprofile.data;

import de.jollyday.HolidayManager;
import javafx.beans.property.*;
import javafx.collections.ObservableList;

public class SyntheticLoadProfile {

    private final ListProperty<Long> inputDataRows = new SimpleListProperty<>(this, "inputDataRows");
    private final LongProperty outputDataRow = new SimpleLongProperty(this, "outputDataRow", -1L);
    private final ObjectProperty<HolidayManager> holidayManager = new SimpleObjectProperty<>(this, "holidayManager");
    private final BooleanProperty holidaysEnabled = new SimpleBooleanProperty(this, "holidaysEnabled", false);

    //TODO list of scheduled events/disturbances


    public ObservableList<Long> getInputDataRows() {
        return inputDataRows.get();
    }

    public void setInputDataRows(ObservableList<Long> inputDataRows) {
        this.inputDataRows.set(inputDataRows);
    }

    public ListProperty<Long> inputDataRowsProperty() {
        return inputDataRows;
    }

    public long getOutputDataRow() {
        return outputDataRow.get();
    }

    public void setOutputDataRow(long outputDataRow) {
        this.outputDataRow.set(outputDataRow);
    }

    public LongProperty outputDataRowProperty() {
        return outputDataRow;
    }

    public HolidayManager getHolidayManager() {
        return holidayManager.get();
    }

    public void setHolidayManager(HolidayManager holidayManager) {
        this.holidayManager.set(holidayManager);
    }

    public ObjectProperty<HolidayManager> holidayManagerProperty() {
        return holidayManager;
    }

    public boolean isHolidaysEnabled() {
        return holidaysEnabled.get();
    }

    public void setHolidaysEnabled(boolean holidaysEnabled) {
        this.holidaysEnabled.set(holidaysEnabled);
    }

    public BooleanProperty holidaysEnabledProperty() {
        return holidaysEnabled;
    }
}
