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

import java.io.File;
import java.util.List;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
public class Announcement extends ManagementDocument {

    private File AnnouncementFile;

    public Announcement(SQLDataSource ds, JsonObject input) throws Exception {
        super(ds, input);

        List<JsonAttribute> listAnnouncementAttributes = getDs().getAttributes(input.getId());

        for (JsonAttribute att : listAnnouncementAttributes) {
            String name = att.getType();

            final String attAnnouncementFile = "Announcement File";
            switch (name) {
                //this.setAnnouncementFile(att.getLatestSample().getValueAsFile());
                case attAnnouncementFile:
                    break;
                default:
                    break;
            }
        }
    }

    public File getAnnouncementFile() {
        return AnnouncementFile;
    }

    public void setAnnouncementFile(File AnnouncementFile) {
        this.AnnouncementFile = AnnouncementFile;
    }

}
