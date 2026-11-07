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
import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A nonconformity (class "Nonconformity"), read from its attribute "Data" as written by the Control Center's
 * {@code NonconformityData}. "Field Tags" is a JSON array; a text (from older writers) is accepted as well.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NonconformityItem {

    public static final String CLASS_NAME = "Nonconformity";
    public static final String PLAN_CLASS_NAME = "NonconformityPlan";
    public static final String PLAN_ATTRIBUTE_PREFIX = "prefix";
    public static final String PLAN_ATTRIBUTE_MEDIUM = "Custom Medium";
    public static final String PLAN_ATTRIBUTE_FIELDS = "Custom Fields";
    public static final String PLAN_ATTRIBUTE_SEU = "Custom SEU";

    /**
     * Immediate action required but no immediate measures.
     */
    public static final String WARNING_IMMEDIATE_MEASURES_MISSING = "IMMEDIATE_MEASURES_MISSING";
    /**
     * Done date without action.
     */
    public static final String WARNING_DONE_WITHOUT_ACTION = "DONE_WITHOUT_ACTION";
    /**
     * Done date in the future.
     */
    public static final String WARNING_DONE_IN_FUTURE = "DONE_IN_FUTURE";

    @JsonProperty("Nr")
    private Integer nr = 0;
    @JsonProperty("Title")
    private String title = "";
    @JsonProperty("Creator")
    private String creator = "";
    @JsonProperty("Responsible Person")
    private String responsible = "";
    @JsonProperty("Description")
    private String description = "";
    @JsonProperty("Cause")
    private String cause = "";
    @JsonProperty("Immediate Measures")
    private String immediateMeasures = "";
    @JsonProperty("Corrective Actions")
    private String correctiveActions = "";
    @JsonProperty("Action")
    private String action = "";
    @JsonProperty("Medium")
    private String medium = "";
    @JsonProperty("Field Tags")
    private JsonNode fields;
    @JsonProperty("SEU")
    private String seu = "";
    @JsonProperty("Create Date")
    private String createDate;
    @JsonProperty("Planned Date")
    private String plannedDate;
    @JsonProperty("Done Date")
    private String doneDate;
    @JsonProperty("Check List")
    private Map<String, Object> checkList;
    @JsonProperty("Deleted")
    private Boolean deleted = false;

    /**
     * @return the parsed nonconformity or null if the JSON cannot be read
     */
    public static NonconformityItem parse(String json) {
        return IsoJson.read(json, NonconformityItem.class);
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
     * @return the field tags: a JSON array, or a text like "[a, b]" or "a;b"
     */
    public List<String> getFields() {
        List<String> list = new ArrayList<>();
        if (fields == null || fields.isNull()) return list;
        if (fields.isArray()) {
            for (JsonNode node : fields) {
                if (node != null && !node.isNull() && !node.asText().trim().isEmpty()) list.add(node.asText().trim());
            }
            return list;
        }
        String text = fields.asText().trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            for (String part : text.substring(1, text.length() - 1).split(",")) {
                if (!part.trim().isEmpty()) list.add(part.trim());
            }
            return list;
        }
        return IsoJson.splitList(text);
    }

    /**
     * @return true without done date
     */
    public boolean isOpen() {
        return IsoJson.parseDate(doneDate) == null;
    }

    /**
     * @return open and the planned date is before today
     */
    public boolean isOverdue(LocalDate today, DateTimeZone zone) {
        LocalDate planned = IsoJson.localDate(plannedDate, zone);
        return isOpen() && planned != null && today != null && planned.isBefore(today);
    }

    /**
     * The checks of the Control Center's {@code NonconformityData.checkForRequirements} (all that apply).
     */
    public List<String> warnings(DateTime now) {
        List<String> warnings = new ArrayList<>();
        boolean immediateRequired = checkList != null && CheckLists.bool(checkList.get("Immediate Action Required"));
        if (immediateRequired && IsoJson.text(immediateMeasures).isEmpty()) {
            warnings.add(WARNING_IMMEDIATE_MEASURES_MISSING);
        }
        DateTime done = IsoJson.parseDate(doneDate);
        if (done != null && IsoJson.text(action).isEmpty()) {
            warnings.add(WARNING_DONE_WITHOUT_ACTION);
        }
        if (done != null && now != null && done.isAfter(now)) {
            warnings.add(WARNING_DONE_IN_FUTURE);
        }
        return warnings;
    }

    /**
     * The nonconformity in the format of the web API (without {@code id} and {@code attachments}).
     */
    public Map<String, Object> toJson(String prefix, DateTime now, LocalDate today, DateTimeZone zone) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("nr", getNr());
        json.put("number", IsoJson.number(prefix, getNr()));
        json.put("title", getTitle());
        json.put("creator", IsoJson.text(creator));
        json.put("responsible", IsoJson.text(responsible));
        json.put("description", IsoJson.text(description));
        json.put("cause", IsoJson.text(cause));
        json.put("immediateMeasures", IsoJson.text(immediateMeasures));
        json.put("correctiveActions", IsoJson.text(correctiveActions));
        json.put("action", IsoJson.text(action));
        json.put("medium", IsoJson.text(medium));
        json.put("fields", getFields());
        json.put("seu", IsoJson.text(seu));
        json.put("createDate", IsoJson.date(createDate));
        json.put("plannedDate", IsoJson.date(plannedDate));
        json.put("doneDate", IsoJson.date(doneDate));
        json.put("open", isOpen());
        json.put("overdue", isOverdue(today, zone));
        json.put("checkList", CheckLists.nonconformity(checkList));
        json.put("warnings", warnings(now));
        return json;
    }
}
