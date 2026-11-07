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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * An action of an action plan (class "Action"), read from its attribute "Data" as written by the Control Center's
 * {@code ActionData} (including the legacy keys "Nr", "Desciption" and "EnpI").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionItem {

    public static final String CLASS_NAME = "Action";
    public static final String PLAN_CLASS_NAME = "Action Plan v2";
    public static final String PLAN_ATTRIBUTE_STATUS = "Custom Status";
    public static final String PLAN_ATTRIBUTE_FIELDS = "Custom Fields";
    public static final String PLAN_ATTRIBUTE_MEDIUM = "Custom Medium";
    public static final String PLAN_ATTRIBUTE_SEU = "Custom SEU";
    public static final String PLAN_ATTRIBUTE_PREFIX = "Nr Prefix";

    @JsonProperty("No")
    @JsonAlias("Nr")
    private Integer no = 0;
    @JsonProperty("Title")
    private String title = "";
    @JsonProperty("Description")
    @JsonAlias("Desciption")
    private String description = "";
    @JsonProperty("Note")
    private String note = "";
    @JsonProperty("From User")
    private String fromUser = "";
    @JsonProperty("Responsible")
    private String responsible = "";
    @JsonProperty("Distributor")
    private String distributor = "";
    @JsonProperty("Create Date")
    private String createDate;
    @JsonProperty("Planned Date")
    private String plannedDate;
    @JsonProperty("Done Date")
    private String doneDate;
    @JsonProperty("Status Tags")
    private String status = "";
    @JsonProperty("Field Tags")
    private String field = "";
    @JsonProperty("Medium Tags")
    private String medium = "";
    @JsonProperty("SEU Tags")
    private String seu = "";
    @JsonProperty("Correction")
    private String correction = "";
    @JsonProperty("Follow Action")
    private String followAction = "";
    @JsonProperty("Alternative Measures")
    private String alternativeMeasures = "";
    @JsonProperty("Affected Process Note")
    private String affectedProcessNote = "";
    @JsonProperty("Energy Flow Note")
    private String energyFlowNote = "";
    @JsonProperty("Evaluation Note")
    private String evaluationNote = "";
    @JsonProperty("NPV")
    private NpvData npv;
    @JsonProperty("Consumption")
    private ConsumptionData consumption;
    @JsonProperty("EnPI")
    @JsonAlias("EnpI")
    private ConsumptionData enpi;
    @JsonProperty("Check List")
    private Map<String, Object> checkList;
    @JsonProperty("Deleted")
    private Boolean deleted = false;

    /**
     * @return the parsed action or null if the JSON cannot be read
     */
    public static ActionItem parse(String json) {
        return IsoJson.read(json, ActionItem.class);
    }

    public int getNo() {
        return no != null ? no : 0;
    }

    public String getTitle() {
        return IsoJson.text(title);
    }

    public String getStatus() {
        return IsoJson.text(status);
    }

    public String getDoneDate() {
        return IsoJson.date(doneDate);
    }

    public ConsumptionData getConsumption() {
        return consumption;
    }

    public ConsumptionData getEnpi() {
        return enpi;
    }

    public NpvData getNpv() {
        return npv;
    }

    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }

    /**
     * Savings since the done date like the Control Center's action table: {@code days · consumption diff / 365}.
     *
     * @return null without done date or consumption
     */
    public Double savingsSinceDone(Integer daysSinceDone) {
        if (daysSinceDone == null || consumption == null || consumption.getDiff() == null) return null;
        return IsoJson.finite(daysSinceDone * (consumption.getDiff() / 365));
    }

    /**
     * The action in the format of the web API (without {@code id} and {@code attachments}).
     *
     * @param prefix the number prefix of the plan
     * @param media  the media of the plan
     * @param today  for {@code daysSinceDone}
     * @param zone   time zone of the dates
     * @param unitOf unit of a data object (null if not readable)
     */
    public Map<String, Object> toJson(String prefix, List<Medium> media, LocalDate today, DateTimeZone zone,
                                      Function<Long, String> unitOf) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("no", getNo());
        json.put("number", IsoJson.number(prefix, getNo()));
        json.put("title", getTitle());
        json.put("description", IsoJson.text(description));
        json.put("note", IsoJson.text(note));
        json.put("fromUser", IsoJson.text(fromUser));
        json.put("responsible", IsoJson.text(responsible));
        json.put("distributor", IsoJson.text(distributor));
        json.put("createDate", IsoJson.date(createDate));
        json.put("plannedDate", IsoJson.date(plannedDate));
        json.put("doneDate", getDoneDate());
        json.put("status", getStatus());
        json.put("field", IsoJson.text(field));
        json.put("medium", IsoJson.text(medium));
        json.put("mediumName", mediumName(media));
        json.put("seu", IsoJson.text(seu));
        json.put("correction", IsoJson.text(correction));
        json.put("followAction", IsoJson.text(followAction));
        json.put("alternativeMeasures", IsoJson.text(alternativeMeasures));
        json.put("affectedProcessNote", IsoJson.text(affectedProcessNote));
        json.put("energyFlowNote", IsoJson.text(energyFlowNote));
        json.put("evaluationNote", IsoJson.text(evaluationNote));
        json.put("npv", npv != null ? npv.toJson() : null);
        json.put("consumption", consumption != null ? consumption.toJson(unit(consumption, unitOf)) : null);
        json.put("enpi", enpi != null ? enpi.toJson(unit(enpi, unitOf)) : null);
        json.put("checkList", CheckLists.action(checkList));
        Integer days = IsoJson.daysSince(doneDate, today, zone);
        json.put("daysSinceDone", days);
        json.put("savingsSinceDone", savingsSinceDone(days));
        return json;
    }

    /**
     * @return the name of the medium id in the plan's media; the stored value if the id is unknown
     */
    String mediumName(List<Medium> media) {
        String id = IsoJson.text(medium);
        Medium found = media != null ? Medium.byId(media, id) : null;
        return found != null ? found.getName() : id;
    }

    private static String unit(ConsumptionData data, Function<Long, String> unitOf) {
        Long id = data.getObjectId();
        return id != null && unitOf != null ? unitOf.apply(id) : null;
    }
}
