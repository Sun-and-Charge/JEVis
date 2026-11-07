/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.iso;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An obligation of the legal register (class "Obligation"), read from its attribute "Data" as written by the Control
 * Center's {@code ObligationData}.
 * <p>
 * The plan ("Index of Legal Provisions") holds the categories and scopes as {@code ;} lists in its attributes
 * "Category" and "Scope"; the Control Center reads them as "category"/"scope", attribute names are matched without
 * case.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Obligation {

    public static final String CLASS_NAME = "Obligation";
    public static final String PLAN_CLASS_NAME = "Index of Legal Provisions";
    public static final String PLAN_ATTRIBUTE_CATEGORY = "Category";
    public static final String PLAN_ATTRIBUTE_SCOPE = "Scope";

    @JsonProperty("uuid")
    private String uuid = "";
    @JsonProperty("Nr")
    private Integer nr = 0;
    @JsonProperty("title")
    private String title = "";
    @JsonProperty("Designation")
    private String designation = "";
    @JsonProperty("Description")
    private String description = "";
    @JsonProperty("importance for the company")
    private String importance = "";
    @JsonProperty("link")
    private String link = "";
    @JsonProperty("category")
    private String category = "";
    @JsonProperty("scope")
    private String scope = "";
    @JsonProperty("Relevance 50001")
    private Boolean relevant = false;
    @JsonProperty("issue date")
    private String issueDate;
    @JsonProperty("current version date")
    private String currentVersionDate;
    @JsonProperty("Date of Examination")
    private String examinationDate;
    @JsonProperty("Deleted")
    private Boolean deleted = false;

    /**
     * @return the parsed obligation or null if the JSON cannot be read
     */
    public static Obligation parse(String json) {
        return IsoJson.read(json, Obligation.class);
    }

    public int getNr() {
        return nr != null ? nr : 0;
    }

    public String getTitle() {
        return IsoJson.text(title);
    }

    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }

    /**
     * The obligation in the format of the web API (without {@code id} and {@code attachments}); {@code link} only if
     * it is an http(s) URL.
     */
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("uuid", IsoJson.text(uuid));
        json.put("nr", getNr());
        json.put("title", getTitle());
        json.put("designation", IsoJson.text(designation));
        json.put("description", IsoJson.text(description));
        json.put("importance", IsoJson.text(importance));
        json.put("link", IsoJson.httpUrl(link));
        json.put("category", IsoJson.text(category));
        json.put("scope", IsoJson.text(scope));
        json.put("relevant", Boolean.TRUE.equals(relevant));
        json.put("issueDate", IsoJson.date(issueDate));
        json.put("currentVersionDate", IsoJson.date(currentVersionDate));
        json.put("examinationDate", IsoJson.date(examinationDate));
        return json;
    }
}
