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

import java.util.List;

import static org.jevis.iso.add.Snippets.getValueString;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
public class Station {
    private long ID;
    private String Name;

    private String DeviceID;
    private String IPAddress;
    private String SubNetMask;
    private String Type;

    public Station(SQLDataSource ds, JsonObject input) throws Exception {
        ID = 0L;
        Name = "";
        DeviceID = "";
        IPAddress = "";
        SubNetMask = "";
        Type = "";
        this.ID = input.getId();
        this.Name = input.getName();

        List<JsonAttribute> listStationAttributes = ds.getAttributes(input.getId());

        for (JsonAttribute att : listStationAttributes) {
            String name = att.getType();

            final String attType = "Type";
            final String attSubNetMask = "SubNet Mask";
            final String attIPAddress = "IP Address";
            final String attDeviceID = "Device ID";
            switch (name) {
                case attDeviceID:
                    this.setDeviceID(getValueString(att, ""));
                    break;
                case attIPAddress:
                    this.setIPAddress(getValueString(att, ""));
                    break;
                case attSubNetMask:
                    this.setSubNetMask(getValueString(att, ""));
                    break;
                case attType:
                    this.setType(getValueString(att, ""));
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

    public String getName() {
        return Name;
    }

    public void setName(String Name) {
        this.Name = Name;
    }

    public String getDeviceID() {
        return DeviceID;
    }

    public void setDeviceID(String DeviceID) {
        this.DeviceID = DeviceID;
    }

    public String getIPAddress() {
        return IPAddress;
    }

    public void setIPAddress(String IPAddress) {
        this.IPAddress = IPAddress;
    }

    public String getSubNetMask() {
        return SubNetMask;
    }

    public void setSubNetMask(String SubNetMask) {
        this.SubNetMask = SubNetMask;
    }

    public String getType() {
        return Type;
    }

    public void setType(String Type) {
        this.Type = Type;
    }

}
