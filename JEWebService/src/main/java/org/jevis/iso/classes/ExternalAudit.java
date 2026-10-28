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
public class ExternalAudit {
    private long ID;
    private String name;
    private Long year;

    private String auditor;
    private String certifier;
    private String auditdate;
    private File auditreportfile;
    private SQLDataSource ds;

    public ExternalAudit(SQLDataSource ds, JsonObject input) throws Exception {
        ID = 0L;
        name = "";
        year = 0L;
        auditor = "";
        certifier = "";
        auditdate = "";
        this.ID = input.getId();
        this.name = input.getName();
        this.ds = ds;

        List<JsonAttribute> listExternalAuditAttributes = getDs().getAttributes(input.getId());

        for (JsonAttribute att : listExternalAuditAttributes) {
            String name = att.getType();

            final String attAuditReportFile = "Audit Report File";
            final String attAuditDate = "Audit Date";
            final String attCertifier = "Certifier";
            final String attAuditor = "Auditor";
            switch (name) {
                //this.setAnnouncementFile(att.getLatestSample().getValueAsFile());

                case attAuditDate:
                    String s = getValueString(att, "");
                    if (!"".equals(s)) {
                        DateTimeFormatter format = DateTimeFormat.forPattern("dd.MM.yyyy");
                        DateTime dt = format.parseDateTime(s);
                        this.setYear((long) dt.getYear());
                    } else {
                        this.setYear(0L);
                    }
                    this.setauditdate(s);
                    break;
                case attAuditReportFile:

                    break;
                case attAuditor:
                    this.setauditor(getValueString(att, ""));
                    break;
                case attCertifier:
                    this.setcertifier(getValueString(att, ""));
                    break;
                default:
                    break;
            }

        }

    }

    public SQLDataSource getDs() {
        return ds;
    }

    public void setDs(SQLDataSource ds) {
        this.ds = ds;
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

    public String getauditor() {
        return auditor;
    }

    public void setauditor(String auditor) {
        this.auditor = auditor;
    }

    public String getcertifier() {
        return certifier;
    }

    public void setcertifier(String certifier) {
        this.certifier = certifier;
    }

    public String getauditdate() {
        return auditdate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setauditdate(String auditdate) {
        this.auditdate = auditdate;
    }

    public File getauditreportfile() {
        return auditreportfile;
    }

    public void setauditreportfile(File auditreportfile) {
        this.auditreportfile = auditreportfile;
    }

    public String getAuditor() {
        return auditor;
    }

    public void setAuditor(String auditor) {
        this.auditor = auditor;
    }

    public String getCertifier() {
        return certifier;
    }

    public void setCertifier(String certifier) {
        this.certifier = certifier;
    }

    public String getAuditdate() {
        return auditdate;
    }

    public void setAuditdate(String auditdate) {
        this.auditdate = auditdate;
    }

    public File getAuditreportfile() {
        return auditreportfile;
    }

    public void setAuditreportfile(File auditreportfile) {
        this.auditreportfile = auditreportfile;
    }

}
