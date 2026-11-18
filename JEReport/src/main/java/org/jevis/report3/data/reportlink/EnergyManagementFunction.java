/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEReport.
 * <p>
 * JEReport is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEReport is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEReport. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEReport is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.report3.data.reportlink;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.iso.ActionItem;
import org.jevis.commons.iso.IsoJson;
import org.jevis.commons.iso.Medium;
import org.jevis.commons.iso.NonconformityItem;
import org.jevis.commons.object.plugin.TargetHelper;
import org.jevis.commons.report.PeriodMode;
import org.jevis.report3.data.report.ReportProperty;
import org.jevis.report3.data.report.intervals.IntervalCalculator;
import org.jevis.report3.data.report.intervals.ReportInterval;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Report link of class {@value #CLASS_NAME}: the actions of the action plans and the nonconformities below a target
 * (organisation or building) for the report period, as lists and sums for the template. Variable = "Template Name".
 * <p>
 * Content (docs/REPORTS-ENMS.md): {@code periodStart}, {@code periodEnd}, {@code actions}, {@code actionsOpen},
 * {@code actionsDoneInPeriod}, {@code actionSummary}, {@code nonconformities}, {@code nonconformitiesOpen},
 * {@code nonconformitiesClosedInPeriod}, {@code nonconformitySummary}.
 */
public class EnergyManagementFunction implements ReportData {
    public static final String CLASS_NAME = "Energy Management Link";
    public static final String ATTRIBUTE_TEMPLATE_NAME = "Template Name";
    public static final String ATTRIBUTE_TARGET = "Target";
    private static final Logger logger = LogManager.getLogger(EnergyManagementFunction.class);
    private static final DateTimeFormatter DATE = DateTimeFormat.forPattern("dd.MM.yyyy");
    private static final int MAX_DEPTH = 12;

    private final JEVisObject linkObject;
    private String templateName;
    private JEVisObject target;

    public EnergyManagementFunction(JEVisObject linkObject) {
        this.linkObject = linkObject;
        try {
            JEVisAttribute nameAttribute = linkObject.getAttribute(ATTRIBUTE_TEMPLATE_NAME);
            templateName = nameAttribute != null && nameAttribute.hasSample() ? nameAttribute.getLatestSample().getValueAsString() : null;
            JEVisAttribute targetAttribute = linkObject.getAttribute(ATTRIBUTE_TARGET);
            if (targetAttribute != null && targetAttribute.hasSample()) {
                TargetHelper helper = new TargetHelper(linkObject.getDataSource(), targetAttribute);
                if (helper.isValid() && helper.targetObjectAccessible() && !helper.getObject().isEmpty()) {
                    target = helper.getObject().get(0);
                }
            }
        } catch (Exception e) {
            logger.error("Could not read energy management link {}", linkObject.getID(), e);
        }
    }

    /** one plan with its parsed items */
    public static final class Plan<T> {
        final String name;
        final String prefix;
        final List<Medium> media;
        final List<T> items;

        public Plan(String name, String prefix, List<Medium> media, List<T> items) {
            this.name = name;
            this.prefix = prefix;
            this.media = media;
            this.items = items;
        }
    }

    @Override
    public Map<String, Object> getReportMap(ReportProperty property, IntervalCalculator intervalCalc) {
        Map<String, Object> templateMap = new HashMap<>();
        if (templateName == null || templateName.trim().isEmpty()) return templateMap;
        ReportInterval interval = intervalCalc.getInterval(PeriodMode.CURRENT.toString().toUpperCase());
        DateTimeZone zone = property != null && property.getTimeZone() != null ? property.getTimeZone() : DateTimeZone.getDefault();
        List<Plan<ActionItem>> actionPlans = new ArrayList<>();
        List<Plan<NonconformityItem>> nonconformityPlans = new ArrayList<>();
        if (target != null) collect(target, actionPlans, nonconformityPlans, 0);
        templateMap.put(templateName.trim(), build(actionPlans, nonconformityPlans, interval.getStart(), interval.getEnd(), zone));
        return templateMap;
    }

    @Override
    public JEVisObject getDataObject() {
        return target;
    }

    @Override
    public LinkStatus getReportLinkStatus(DateTime end) {
        /* plans need no data up to the report end; the report must not wait for them */
        return target != null ? new LinkStatus(true, "ok")
                : new LinkStatus(false, "Energy management link " + linkObject.getID() + " has no readable target");
    }

    private void collect(JEVisObject object, List<Plan<ActionItem>> actionPlans, List<Plan<NonconformityItem>> nonconformityPlans, int depth) {
        if (depth > MAX_DEPTH) return;
        try {
            String className = object.getJEVisClassName();
            if (ActionItem.PLAN_CLASS_NAME.equals(className)) {
                List<ActionItem> items = new ArrayList<>();
                for (JEVisObject item : below(object, ActionItem.CLASS_NAME)) {
                    ActionItem action = ActionItem.parse(data(item));
                    if (action != null && !action.isDeleted()) items.add(action);
                }
                actionPlans.add(new Plan<>(object.getName(), planText(object, ActionItem.PLAN_ATTRIBUTE_PREFIX),
                        Medium.parseList(planText(object, ActionItem.PLAN_ATTRIBUTE_MEDIUM)), items));
                return;
            }
            if (NonconformityItem.PLAN_CLASS_NAME.equals(className)) {
                List<NonconformityItem> items = new ArrayList<>();
                for (JEVisObject item : below(object, NonconformityItem.CLASS_NAME)) {
                    NonconformityItem nonconformity = NonconformityItem.parse(data(item));
                    if (nonconformity != null && !nonconformity.isDeleted()) items.add(nonconformity);
                }
                nonconformityPlans.add(new Plan<>(object.getName(), planText(object, NonconformityItem.PLAN_ATTRIBUTE_PREFIX),
                        Collections.emptyList(), items));
                return;
            }
            for (JEVisObject child : object.getChildren()) collect(child, actionPlans, nonconformityPlans, depth + 1);
        } catch (Exception e) {
            logger.warn("Could not read {} for the energy management link: {}", object.getID(), e.getMessage());
        }
    }

    private static List<JEVisObject> below(JEVisObject plan, String className) throws Exception {
        List<JEVisObject> found = new ArrayList<>();
        Deque<JEVisObject> todo = new ArrayDeque<>(plan.getChildren());
        while (!todo.isEmpty()) {
            JEVisObject current = todo.poll();
            if (className.equals(current.getJEVisClassName())) found.add(current);
            else todo.addAll(current.getChildren());
        }
        return found;
    }

    private static String data(JEVisObject item) {
        try {
            JEVisAttribute attribute = item.getAttribute(IsoJson.ATTRIBUTE_DATA);
            if (attribute == null || !attribute.hasSample()) return null;
            JEVisSample sample = attribute.getLatestSample();
            if (sample.getValueAsFile() != null) return new String(sample.getValueAsFile().getBytes(), StandardCharsets.UTF_8);
            return sample.getValueAsString();
        } catch (Exception e) {
            return null;
        }
    }

    /** latest value of an attribute of the plan, the name matched without case */
    private static String planText(JEVisObject plan, String name) {
        try {
            for (JEVisAttribute attribute : plan.getAttributes()) {
                if (attribute.getName().equalsIgnoreCase(name) && attribute.hasSample()) {
                    return attribute.getLatestSample().getValueAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * The template content for the period [start, end).
     */
    public static Map<String, Object> build(List<Plan<ActionItem>> actionPlans, List<Plan<NonconformityItem>> nonconformityPlans,
                                            DateTime start, DateTime end, DateTimeZone zone) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("periodStart", start.withZone(zone).toString(DATE));
        map.put("periodEnd", end.withZone(zone).minusMillis(1).toString(DATE));
        LocalDate today = end.withZone(zone).minusMillis(1).toLocalDate();

        List<Map<String, Object>> actions = new ArrayList<>();
        List<Map<String, Object>> open = new ArrayList<>();
        List<Map<String, Object>> doneInPeriod = new ArrayList<>();
        double plannedSaving = 0, achievedSaving = 0, savingPerYear = 0, investment = 0;
        int done = 0;
        for (Plan<ActionItem> plan : actionPlans) {
            for (ActionItem action : plan.items) {
                Map<String, Object> raw = action.toJson(plan.prefix, plan.media, today, zone, id -> null);
                DateTime created = IsoJson.parseDate((String) raw.get("createDate"));
                if (created != null && !created.isBefore(end)) continue;
                DateTime doneDate = IsoJson.parseDate(action.getDoneDate());
                boolean isDone = doneDate != null && doneDate.isBefore(end);
                boolean isDoneInPeriod = isDone && !doneDate.isBefore(start);
                Double diff = action.getConsumption() != null ? IsoJson.finite(action.getConsumption().getDiff()) : null;
                Double saving = action.getNpv() != null ? IsoJson.finite(action.getNpv().getSaving()) : null;
                Double invest = action.getNpv() != null ? IsoJson.finite(action.getNpv().getInvestment()) : null;

                Map<String, Object> row = new LinkedHashMap<>(raw);
                row.put("plan", plan.name);
                row.put("createDateText", text(raw.get("createDate"), zone));
                row.put("plannedDateText", text(raw.get("plannedDate"), zone));
                row.put("doneDateText", isDone ? text(action.getDoneDate(), zone) : "");
                row.put("done", isDone);
                row.put("doneInPeriod", isDoneInPeriod);
                row.put("savingKWh", diff != null ? diff : 0d);
                row.put("savingEuroPerYear", saving != null ? saving : 0d);
                row.put("investment", invest != null ? invest : 0d);
                actions.add(row);
                if (isDone) {
                    done++;
                    if (diff != null) achievedSaving += diff;
                    if (saving != null) savingPerYear += saving;
                    if (isDoneInPeriod) doneInPeriod.add(row);
                } else {
                    open.add(row);
                    if (diff != null) plannedSaving += diff;
                }
                if (invest != null) investment += invest;
            }
        }
        map.put("actions", actions);
        map.put("actionsOpen", open);
        map.put("actionsDoneInPeriod", doneInPeriod);
        Map<String, Object> actionSummary = new LinkedHashMap<>();
        actionSummary.put("total", actions.size());
        actionSummary.put("open", open.size());
        actionSummary.put("done", done);
        actionSummary.put("doneInPeriod", doneInPeriod.size());
        actionSummary.put("plannedSavingKWh", plannedSaving);
        actionSummary.put("achievedSavingKWh", achievedSaving);
        actionSummary.put("savingEuroPerYear", savingPerYear);
        actionSummary.put("investment", investment);
        map.put("actionSummary", actionSummary);

        List<Map<String, Object>> nonconformities = new ArrayList<>();
        List<Map<String, Object>> ncOpen = new ArrayList<>();
        List<Map<String, Object>> ncClosed = new ArrayList<>();
        int newInPeriod = 0, overdue = 0;
        for (Plan<NonconformityItem> plan : nonconformityPlans) {
            for (NonconformityItem item : plan.items) {
                Map<String, Object> raw = item.toJson(plan.prefix, end.minusMillis(1), today, zone);
                DateTime created = IsoJson.parseDate((String) raw.get("createDate"));
                if (created != null && !created.isBefore(end)) continue;
                DateTime closed = IsoJson.parseDate((String) raw.get("doneDate"));
                boolean isClosed = closed != null && closed.isBefore(end);
                DateTime planned = IsoJson.parseDate((String) raw.get("plannedDate"));
                boolean isOverdue = !isClosed && planned != null && planned.isBefore(end);

                Map<String, Object> row = new LinkedHashMap<>(raw);
                row.put("plan", plan.name);
                row.put("createDateText", text(raw.get("createDate"), zone));
                row.put("plannedDateText", text(raw.get("plannedDate"), zone));
                row.put("doneDateText", isClosed ? text(raw.get("doneDate"), zone) : "");
                row.put("open", !isClosed);
                row.put("overdue", isOverdue);
                nonconformities.add(row);
                if (created != null && !created.isBefore(start)) newInPeriod++;
                if (isClosed) {
                    if (!closed.isBefore(start)) ncClosed.add(row);
                } else {
                    ncOpen.add(row);
                    if (isOverdue) overdue++;
                }
            }
        }
        map.put("nonconformities", nonconformities);
        map.put("nonconformitiesOpen", ncOpen);
        map.put("nonconformitiesClosedInPeriod", ncClosed);
        Map<String, Object> ncSummary = new LinkedHashMap<>();
        ncSummary.put("total", nonconformities.size());
        ncSummary.put("open", ncOpen.size());
        ncSummary.put("overdue", overdue);
        ncSummary.put("newInPeriod", newInPeriod);
        ncSummary.put("closedInPeriod", ncClosed.size());
        map.put("nonconformitySummary", ncSummary);
        return map;
    }

    private static String text(Object isoDate, DateTimeZone zone) {
        if (!(isoDate instanceof String)) return "";
        DateTime date = IsoJson.parseDate((String) isoDate);
        return date != null ? date.withZone(zone).toString(DATE) : "";
    }
}
