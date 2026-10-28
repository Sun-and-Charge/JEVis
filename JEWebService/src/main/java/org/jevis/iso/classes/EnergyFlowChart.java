/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.iso.classes;

import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.util.List;

import static org.jevis.iso.add.Snippets.getValueString;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
public class EnergyFlowChart {
    private long ID;
    private String name;
    private Long year;

    private File ImageFile;
    private File OriginalFile;

    private String energyflowchartdate;

    public EnergyFlowChart(SQLDataSource ds, JsonObject input) throws Exception {
        ID = 0L;
        name = "";
        year = 0L;
        energyflowchartdate = "";
        this.ID = input.getId();
        this.name = input.getName();

        List<JsonAttribute> listEnergyFlowChartAttributes = ds.getAttributes(input.getId());

        for (JsonAttribute att : listEnergyFlowChartAttributes) {
            String name = att.getType();

            final String attEnergyFlowChartDate = "Created On";
            final String attOriginalFile = "Original File";
            final String attImageFile = "Image File";
            switch (name) {
                case attImageFile:
                    //this.setEnergySupplier(Organisation.getValueString(att, ""));
                    break;
                case attOriginalFile:

                    break;
                case attEnergyFlowChartDate:
                    String s = getValueString(att, "");
                    DateTimeFormatter format = DateTimeFormat.forPattern("dd.MM.yyyy");
                    if (!"".equals(s)) {
                        DateTime dt = format.parseDateTime(s);
                        this.setYear((long) dt.getYear());
                    } else {
                        this.setYear(0L);
                    }
                    this.setEnergyflowchartdate(s);
                    break;
                default:
                    break;
            }
        }
    }

    public long getID() {
        return ID;
    }

    public void setID(long ID) {
        this.ID = ID;
    }

    public Long getYear() {
        return year;
    }

    public void setYear(Long year) {
        this.year = year;
    }

    public File getImageFile() {
        return ImageFile;
    }

    public void setImageFile(File ImageFile) {
        this.ImageFile = ImageFile;
    }

    public File getOriginalFile() {
        return OriginalFile;
    }

    public void setOriginalFile(File OriginalFile) {
        this.OriginalFile = OriginalFile;
    }

    public String getEnergyflowchartdate() {
        return energyflowchartdate;
    }

    public void setEnergyflowchartdate(String energyflowchartdate) {
        this.energyflowchartdate = energyflowchartdate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "EnergyFlowChart{" + "ID=" + ID + ", name=" + name + ", ImageFile=" + ImageFile + ", OriginalFile=" + OriginalFile + '}';
    }

}
