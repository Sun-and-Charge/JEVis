/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
package org.jevis.jeconfig.plugin.object.extension.OPC;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jevis.jeopc.OpcReference;
import org.jevis.jeopc.OpcValue;

public class Node {

    private final SimpleObjectProperty<OpcReference> descriptionProperty;
    private SimpleObjectProperty<String> nodeIdProperty = new SimpleObjectProperty<>();
    private SimpleStringProperty stringNodeID = new SimpleStringProperty("");
    private SimpleStringProperty idStringProperty = new SimpleStringProperty("");
    private final StringProperty pathProperty;
    private StringProperty stringProperty = new SimpleStringProperty("hmmm");
    private StringProperty typeProperty = new SimpleStringProperty("");
    private OpcValue dataValue;
    private String TrendID;
    private String logInterval = "";
    private String name;
    public boolean selected;
    private String trendType = GERNERIC_TREND;

    public static final String GERNERIC_TREND = "generic";
    public static final String BACNET_TREND = "bacnet";

    public Node(OpcReference referenceDescription, String xpath, OpcValue dataValue) {
        pathProperty = new SimpleStringProperty(xpath);
        descriptionProperty=new SimpleObjectProperty<>(referenceDescription);
        this.dataValue = dataValue;
        name = descriptionProperty.get().getName();

        try {
            String nodeId = referenceDescription.getNodeId();
            nodeIdProperty.set(nodeId);
            stringNodeID.set(nodeId);
            idStringProperty.set(referenceDescription.getName());
            stringProperty.setValue(referenceDescription.toString());
            typeProperty.set(referenceDescription.getNodeClass());
        }catch ( Exception ex){
            ex.printStackTrace();
        }


    }

    public String readData(){
        try {
            if (descriptionProperty.get().isVariable() && dataValue != null && dataValue.getValue() != null) {
                return dataValue.getValue().toString();
            } else {
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }


    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getTrendID() {
        return TrendID;
    }

    public void setTrendID(String trendID) {
        TrendID = trendID;
    }

    public String getLogInterval() {
        return logInterval;
    }

    public void setLogInterval(String logInterval) {
        this.logInterval = logInterval;
    }

    public OpcReference getDescriptionProperty() {
        return descriptionProperty.get();
    }

    public SimpleObjectProperty<OpcReference> descriptionPropertyProperty() {
        return descriptionProperty;
    }

    public void setDescriptionProperty(OpcReference descriptionProperty) {
        this.descriptionProperty.set(descriptionProperty);
    }

    public String getNodeIdProperty() {
        return nodeIdProperty.get();
    }

    public SimpleObjectProperty<String> nodeIdPropertyProperty() {
        return nodeIdProperty;
    }

    public void setNodeIdProperty(String nodeIdProperty) {
        this.nodeIdProperty.set(nodeIdProperty);
    }

    public String getStringNodeID() {
        return stringNodeID.get();
    }

    public SimpleStringProperty stringNodeIDProperty() {
        return stringNodeID;
    }

    public void setStringNodeID(String stringNodeID) {
        this.stringNodeID.set(stringNodeID);
    }

    public String getIdStringProperty() {
        return idStringProperty.get();
    }

    public SimpleStringProperty idStringPropertyProperty() {
        return idStringProperty;
    }

    public void setIdStringProperty(String idStringProperty) {
        this.idStringProperty.set(idStringProperty);
    }

    public String getPathProperty() {
        return pathProperty.get();
    }

    public StringProperty pathPropertyProperty() {
        return pathProperty;
    }

    public void setPathProperty(String pathProperty) {
        this.pathProperty.set(pathProperty);
    }

    public String getStringProperty() {
        return stringProperty.get();
    }

    public StringProperty stringPropertyProperty() {
        return stringProperty;
    }

    public void setStringProperty(String stringProperty) {
        this.stringProperty.set(stringProperty);
    }

    public String getTypeProperty() {
        return typeProperty.get();
    }

    public StringProperty typePropertyProperty() {
        return typeProperty;
    }

    public void setTypeProperty(String typeProperty) {
        this.typeProperty.set(typeProperty);
    }

    public OpcValue getDataValue() {
        return dataValue;
    }

    public void setDataValue(OpcValue dataValue) {
        this.dataValue = dataValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTrendType() {
        return trendType;
    }

    public void setTrendType(String trendType) {
        this.trendType = trendType;
    }

    @Override
    public String toString() {
        return "Node{" +
                "descriptionProperty=" + descriptionProperty +
                ", nodeIdProperty=" + nodeIdProperty +
                ", stringNodeID=" + stringNodeID +
                ", idStringProperty=" + idStringProperty +
                ", pathProperty=" + pathProperty +
                ", stringProperty=" + stringProperty +
                ", typeProperty=" + typeProperty +
                ", dataValue=" + dataValue +
                ", TrendID='" + TrendID + '\'' +
                ", logInterval='" + logInterval + '\'' +
                ", name='" + name + '\'' +
                ", selected=" + selected +
                ", trendType='" + trendType + '\'' +
                '}';
    }
}
