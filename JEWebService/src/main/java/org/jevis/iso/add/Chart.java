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
package org.jevis.iso.add;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
public class Chart {

    private long ID = 0;
    private String name = new String();
    private String from = new String();
    private String until = new String();
    private String TemplateChoice = new String();
    private List<String> xValuesString = new ArrayList<>();
    private List<Double> xValuesDouble = new ArrayList<>();
    private List<Double> yValuesDouble = new ArrayList<>();
    private List<ChartLine> lines = new ArrayList<>();

    public Chart(String type) {
        switch (type) {
            case "line":
                TemplateChoice = "chartLine";
                break;
            default:
                break;
        }
    }

    public long getID() {
        return ID;
    }

    public void setID(long ID) {
        this.ID = ID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Double> getyValuesDouble() {
        return yValuesDouble;
    }

    public void setyValuesDouble(List<Double> yValuesDouble) {
        this.yValuesDouble = yValuesDouble;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getUntil() {
        return until;
    }

    public void setUntil(String until) {
        this.until = until;
    }

    public List<String> getxValuesString() {
        return xValuesString;
    }

    public void setxValuesString(List<String> xValuesString) {
        this.xValuesString = xValuesString;
    }

    public List<Double> getxValuesDouble() {
        return xValuesDouble;
    }

    public void setxValuesDouble(List<Double> xValuesDouble) {
        this.xValuesDouble = xValuesDouble;
    }

    public List<ChartLine> getLines() {
        return lines;
    }

    public void setLines(List<ChartLine> lines) {
        this.lines = lines;
    }

    public String getOutput() {
        String output = "";
        if (ID != 0 && lines != null) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("ID", getID());
            map.put("chartname", getName());

            map.put("fromValue", getFrom());
            map.put("untilValue", getUntil());

            map.put("xvalues", getxValuesString());
            map.put("yvalues", getyValuesDouble());

            TemplateChooser tc = new TemplateChooser(map, TemplateChoice);

            output = tc.getOutput();
        } else {
            output = "not enough data!";
        }
        return output;
    }

    @Override
    public String toString() {
        return "Chart{" + "ID=" + ID + ", name=" + name + ", TemplateChoice=" + TemplateChoice + '}';
    }

}
