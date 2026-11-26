/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEAlarm.
 * <p>
 * JEAlarm is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEAlarm is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEAlarm. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEAlarm is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jealarm;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.JEVisFileImp;
import org.jevis.commons.alarm.*;
import org.jevis.commons.constants.AlarmConstants;
import org.jevis.commons.constants.NoteConstants;
import org.jevis.commons.dataprocessing.CleanDataObject;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.datetime.DateHelper;
import org.jevis.commons.datetime.Period;
import org.jevis.commons.datetime.PeriodHelper;
import org.jevis.commons.json.*;
import org.jevis.commons.object.plugin.TargetHelper;
import org.jevis.commons.utils.CommonMethods;
import org.jevis.jenotifier.mode.SendNotification;
import org.jevis.jenotifier.notifier.Email.EmailNotification;
import org.jevis.jenotifier.notifier.Email.EmailNotificationDriver;
import org.jevis.jenotifier.notifier.Email.EmailServiceProperty;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */

public class AlarmProcess {
    private static final Logger logger = LogManager.getLogger(AlarmConfiguration.class);
    private static final String RAW_DATA_CLASS_NAME = "Data";
    private static final String CLEAN_DATA_CLASS_NAME = "Clean Data";
    private static final String DATA_DIRECTORY_NAME = "Data Directory";
    private static final String VALUE_ATTRIBUTE = "Value";
    private static final String ALARM_ENABLED_ATTRIBUTE = "Alarm Enabled";
    private static final String ALARM_LOG_ATTRIBUTE = "Alarm Log";
    private static final String NOTIFICATION_CLASS = "Notification";
    private final AlarmConfiguration alarmConfiguration;
    private final JEVisDataSource ds;
    private JEVisClass alarmConfigurationClass;
    private JEVisClass rawDataClass;
    private JEVisClass cleanDataClass;
    private JEVisClass dataDirectoryClass;
    private JEVisClass notificationClass;
    private DateTime start;
    private DateTime end;

    public AlarmProcess(AlarmConfiguration alarmConfiguration) {
        this.alarmConfiguration = alarmConfiguration;
        this.ds = alarmConfiguration.getDs();

        try {
            this.alarmConfigurationClass = ds.getJEVisClass(AlarmConfiguration.CLASS_NAME);
            this.rawDataClass = ds.getJEVisClass(RAW_DATA_CLASS_NAME);
            this.cleanDataClass = ds.getJEVisClass(CLEAN_DATA_CLASS_NAME);
            this.dataDirectoryClass = ds.getJEVisClass(DATA_DIRECTORY_NAME);
            this.notificationClass = ds.getJEVisClass(NOTIFICATION_CLASS);
        } catch (JEVisException e) {
            e.printStackTrace();
        }
    }


    public void start() {
        List<JEVisObject> allCleanDataObjects = new ArrayList<>();

        DateTime now = DateTime.now();
        Period alarmPeriod = alarmConfiguration.getAlarmPeriod();
        Boolean boundToData = alarmConfiguration.isBoundToData();
        DateHelper dateHelper = null;
        start = alarmConfiguration.getTimeStamp();
        dateHelper = PeriodHelper.getDateHelper(alarmConfiguration.getObject(), alarmPeriod, dateHelper, start);
        if (start == null) {
            // no "Time Stamp" set yet: begin with the last complete alarm period instead of failing
            start = firstStart(alarmPeriod, alarmConfiguration.getDateTimeZone(), now, dateHelper);
            logger.info("Alarm configuration {} has no time stamp, starting at {}", alarmConfiguration.getObject().getID(), start);
        }

        if (alarmPeriod != Period.NONE) {
            end = PeriodHelper.getNextPeriod(start, alarmPeriod, alarmConfiguration.getDateTimeZone(), 1, null).minusMillis(1);
        } else {
            end = now.minusMillis(1);
        }

        /* data outages and the daily summary are checked in every run, independent of the alarm period */
        Set<Long> rowsInOutage = checkOutages(now);

        if (boundToData || end.isBefore(now)) {
            try {
                allCleanDataObjects = getAllCorrespondingCleanDataObjects();
            } catch (JEVisException e) {
                logger.error("Could not get list of all Clean Data Objects.");
            }

            boolean hasData = true;
            List<JEVisObject> enabledAlarms = new ArrayList<>();
            for (JEVisObject obj : allCleanDataObjects) {
                try {
                    CleanDataObject cleanDataObject = new CleanDataObject(obj);
                    JEVisAttribute alarmEnabledAttribute = cleanDataObject.getAlarmEnabledAttribute();
                    JEVisSample latestSample = alarmEnabledAttribute != null && alarmEnabledAttribute.hasSample()
                            ? alarmEnabledAttribute.getLatestSample() : null;
                    /* only rows with "Alarm Enabled" switched on are checked (before, a row switched off was checked too) */
                    boolean alarmEnabled = isAlarmEnabled(latestSample);
                    if (!alarmEnabled) continue;
                    enabledAlarms.add(obj);

                    DateTime lastTS = cleanDataObject.getFirstDate();
                    /* a row in data outage no longer holds back the other rows of the configuration */
                    if (alarmPeriod != Period.NONE && alarmEnabled && !lastTS.isAfter(end) && !rowsInOutage.contains(obj.getID())) {
                        logger.debug("Object {}:{} has last ts of {} that is not after {}", obj.getName(), obj.getID(), lastTS, end);
                        hasData = false;
                        break;
                    }
                }catch(Exception ex){
                    logger.error("Error in obj: {}",obj,ex);
                }
            }

            if (hasData) {
                List<Alarm> activeAlarms = getActiveAlarms(enabledAlarms);

                if (!activeAlarms.isEmpty()) {
                    AlarmTable alarmTable = new AlarmTable(ds, activeAlarms);

                    boolean sentAlarm = sendAlarm(alarmTable);

                    if (sentAlarm) logger.info("Sent notification.");
                    else logger.info("Did not send notification.");

                    if (start != null && end != null && end.isAfter(start)) {
                        try {

                            List<JsonAlarm> jsonAlarmList = new ArrayList<>();
                            for (Alarm alarm : activeAlarms) {
                                JsonAlarm jsonAlarm = new JsonAlarm();
                                jsonAlarm.setAlarmType(alarm.getAlarmType());
                                jsonAlarm.setAttribute(alarm.getAttribute().getName());
                                jsonAlarm.setIsValue(alarm.getIsValue());
                                jsonAlarm.setOperator(alarm.getOperator());
                                jsonAlarm.setLogValue(alarm.getLogValue());
                                jsonAlarm.setObject(alarm.getObject().getID());
                                jsonAlarm.setShouldBeValue(alarm.getSetValue());
                                jsonAlarm.setTimeStamp(alarm.getTimeStamp().toString());
                                jsonAlarm.setTolerance(alarm.getTolerance());
                                jsonAlarmList.add(jsonAlarm);
                            }
                            try {
                                JEVisFileImp jsonFile = new JEVisFileImp(
                                        alarmConfiguration.getName() + "_" + DateTime.now().toString("yyyyMMddHHmm") + ".json"
                                        , JsonTools.prettyObjectMapper().writeValueAsString(jsonAlarmList).getBytes(StandardCharsets.UTF_8));
                                JEVisSample newSample = alarmConfiguration.getFileLogAttribute().buildSample(new DateTime(), jsonFile);
                                newSample.commit();
                            } catch (JsonProcessingException e) {
                                logger.error("JsonProcessingException. Could not build sample with new time stamp.", e);
                            } catch (FileNotFoundException e) {
                                logger.error("FileNotFoundException. Could not build sample with new time stamp.", e);
                            } catch (IOException e) {
                                logger.error("IOException. Could not build sample with new time stamp.", e);
                            }

                            alarmConfiguration.setChecked(false);
                        } catch (JEVisException e) {
                            logger.error("Could not build sample with new time stamp.");
                        }
                    }
                }

                try {
                    finish();
                } catch (Exception e) {
                    logger.error("Could not set new start record.");
                }
            } else {
                logger.warn("No new Data.");
            }
        }
    }

    /**
     * @param latestSample the latest sample of "Alarm Enabled", null if there is none
     * @return whether the alarms of the row are switched on (a missing or unreadable value counts as off)
     */
    static boolean isAlarmEnabled(JEVisSample latestSample) {
        if (latestSample == null) return false;
        try {
            return Boolean.TRUE.equals(latestSample.getValueAsBoolean());
        } catch (Exception e) {
            logger.error("Error while getting alarm enabled attribute sample", e);
            return false;
        }
    }

    /**
     * Start of the last complete alarm period before {@code now}, used when the configuration has no time stamp yet.
     */
    static DateTime firstStart(Period alarmPeriod, DateTimeZone zone, DateTime now, DateHelper dateHelper) {
        DateTime local = now.withZone(zone != null ? zone : DateTimeZone.getDefault());
        DateTime periodStart;
        switch (alarmPeriod) {
            case MINUTELY:
                periodStart = local.minuteOfDay().roundFloorCopy();
                break;
            case QUARTER_HOURLY:
                periodStart = local.hourOfDay().roundFloorCopy().plusMinutes((local.getMinuteOfHour() / 15) * 15);
                break;
            case HOURLY:
                periodStart = local.hourOfDay().roundFloorCopy();
                break;
            case WEEKLY:
                periodStart = local.withDayOfWeek(1).withTimeAtStartOfDay();
                break;
            case MONTHLY:
                periodStart = local.withDayOfMonth(1).withTimeAtStartOfDay();
                break;
            case QUARTERLY:
                periodStart = local.withDayOfMonth(1).withMonthOfYear(((local.getMonthOfYear() - 1) / 3) * 3 + 1).withTimeAtStartOfDay();
                break;
            case YEARLY:
                periodStart = local.withDayOfYear(1).withTimeAtStartOfDay();
                break;
            case NONE:
                return local.minusDays(1);
            default:
                periodStart = local.withTimeAtStartOfDay();
                break;
        }
        if ((alarmPeriod == Period.CUSTOM || alarmPeriod == Period.CUSTOM2) && dateHelper == null) {
            return periodStart.minusDays(1);
        }
        return PeriodHelper.getPriorStartRecord(periodStart, alarmPeriod, dateHelper);
    }

    private void finish() throws JEVisException {
        DateTime newStartRecordTime;
        if (alarmConfiguration.getAlarmPeriod() != Period.NONE) {
            newStartRecordTime = PeriodHelper.getNextPeriod(start, alarmConfiguration.getAlarmPeriod(), alarmConfiguration.getDateTimeZone(), 1, null);
        } else {
            alarmConfiguration.getTimeStampAttribute().deleteAllSample();
            newStartRecordTime = DateTime.now();
        }
        alarmConfiguration.getTimeStampAttribute().buildSample(new DateTime(), newStartRecordTime.toString()).commit();
    }

    /**
     * Outage check and daily summary (docs/ALARMS-OUTAGE.md); never throws.
     *
     * @return the rows in data outage
     */
    private Set<Long> checkOutages(DateTime now) {
        try {
            List<JEVisObject> rows = new ArrayList<>();
            for (JEVisObject obj : getAllCorrespondingCleanDataObjects()) {
                try {
                    JEVisAttribute enabled = obj.getAttribute(ALARM_ENABLED_ATTRIBUTE);
                    if (enabled != null && isAlarmEnabled(enabled.getLatestSample())) rows.add(obj);
                } catch (Exception e) {
                    logger.debug("Skipped {} in outage check", obj, e);
                }
            }
            OutageMonitor monitor = new OutageMonitor(ds, alarmConfiguration, this::mail);
            Set<Long> inOutage = monitor.run(rows, dataSources(), now);
            monitor.dailySummary(now);
            return inOutage;
        } catch (Exception e) {
            logger.error("Outage check of {} failed", alarmConfiguration.getObject().getID(), e);
            return new HashSet<>();
        }
    }

    /** data sources of the configuration: selected ones, or with scope COMPLETE all below the building */
    private List<JEVisObject> dataSources() {
        List<JEVisObject> sources = new ArrayList<>();
        try {
            switch (alarmConfiguration.getAlarmScope()) {
                case SELECTED:
                    for (JEVisObject o : alarmConfiguration.getAlarmObjects()) if (OutageMonitor.isA(o, "Data Source")) sources.add(o);
                    break;
                case COMPLETE:
                case WITHOUT_SELECTED: {
                    JEVisObject cfg = alarmConfiguration.getObject();
                    JEVisObject building = cfg.getParents().get(0).getParents().get(0);
                    OutageMonitor.collect(building, "Data Source", sources);
                    if (alarmConfiguration.getAlarmScope() == AlarmScope.WITHOUT_SELECTED) sources.removeAll(alarmConfiguration.getAlarmObjects());
                    break;
                }
                default:
                    break;
            }
        } catch (Exception e) {
            logger.error("Could not find the data sources of {}", alarmConfiguration.getObject().getID(), e);
        }
        return sources;
    }

    /** a mail with the configuration's notification and its own subject; false if it has no notification */
    private boolean mail(String subject, String html) {
        try {
            EmailNotification notification = new EmailNotification();
            notification.setNotificationObject(getNotificationObject());
            notification.setIsHTML(true);
            notification.setSubject(subject);
            notification.setMessage(html);
            sendNotification(notification);
            return true;
        } catch (Exception e) {
            logger.warn("No notification for {}: {}", alarmConfiguration.getObject().getID(), e.getMessage());
            return false;
        }
    }

    public boolean sendAlarm(AlarmTable alarmTable) {
        /* no mail without a listed alarm (e.g. only alarms in silent times) */
        String alarms = "";
        String limits = "";
        try {
            alarms = alarmTable.getAlarmTable();
        } catch (Exception e) {
            logger.error("Could not get alarm table string.");
        }
        if (alarmConfiguration.includeLimits()) {
            try {
                limits = alarmTable.getTableString();
            } catch (Exception e) {
                logger.error("Could not get limit table string.");
            }
        }
        if (alarms.isEmpty() && (limits == null || limits.isEmpty())) {
            logger.info("Nothing to report for {}, no mail", alarmConfiguration.getObject().getID());
            return false;
        }

        try {
            EmailNotification emailNotification = new EmailNotification();
            emailNotification.setNotificationObject(getNotificationObject());
            emailNotification.setIsHTML(true);

            StringBuilder sb = new StringBuilder();

            sb.append("<html>");

            sb.append("<br>");
            sb.append("<br>");
            sb.append(emailNotification.getMessage());
            sb.append("<br>");
            sb.append("<br>");

            try {
                sb.append(alarmTable.getAlarmTable());
                sb.append("<br>");
                sb.append("<br>");
            } catch (Exception e) {
                logger.error("Could not get alarm table string.");
            }

            if (alarmConfiguration.includeLimits()) {
                try {
                    sb.append(alarmTable.getTableString());
                    sb.append("<br>");
                    sb.append("<br>");
                } catch (Exception e) {
                    logger.error("Could not get limit table string.");
                }
            }

            sb.append("</html>");

            emailNotification.setMessage(sb.toString());

            sendNotification(emailNotification);
            return true;
        } catch (Exception e) {
            logger.warn("No notification object.");
            return false;
        }
    }

    private JEVisObject getNotificationObject() {
        try {
            List<JEVisObject> notificationObjects = alarmConfiguration.getObject().getChildren(notificationClass, true);
            if (notificationObjects.size() == 1) {
                return notificationObjects.get(0);
            } else {
                throw new IllegalStateException("Too many or no Notification Object for Object: id: " + alarmConfiguration.getObject().getID() + " and name: " + alarmConfiguration.getObject().getName());
            }
        } catch (JEVisException ex) {
            throw new RuntimeException("Error while parsing Notification Object for Object: id: " + alarmConfiguration.getObject().getID() + " and name: " + alarmConfiguration.getObject().getName(), ex);
        }
    }

    private void sendNotification(EmailNotification emailNotification) {
        try {

            EmailServiceProperty service = getReportService();

            JEVisObject emailNotificationDriverObject = ds.getObject(service.getMailID());

            EmailNotificationDriver emailNotificationDriver = new EmailNotificationDriver();
            emailNotificationDriver.setNotificationDriverObject(emailNotificationDriverObject);

            SendNotification sn = new SendNotification(emailNotification, emailNotificationDriver);
            sn.run();

        } catch (Exception ex) {
            logger.error(ex);
        }
    }

    private EmailServiceProperty getReportService() {

        EmailServiceProperty service = new EmailServiceProperty();
        try {
            JEVisClass jeVisClass = ds.getJEVisClass("JEReport");
            List<JEVisObject> reportServies = ds.getObjects(jeVisClass, true);
            if (reportServies.size() == 1) {
                service.initialize(reportServies.get(0));
            }
        } catch (JEVisException ex) {
            logger.error("error while getting report service", ex);
        }
        return service;
    }


    private List<Alarm> getActiveAlarms(List<JEVisObject> allCleanDataObjects) {
        List<Alarm> activeAlarms = new ArrayList<>();

        for (JEVisObject cleanData : allCleanDataObjects) {
            try {

                JEVisAttribute valueAtt = cleanData.getAttribute(VALUE_ATTRIBUTE);
                JEVisAttribute alarmLogAttribute = cleanData.getAttribute(ALARM_LOG_ATTRIBUTE);

                List<JEVisSample> valueSamples = new ArrayList<>();
                if (alarmConfiguration.getAlarmPeriod() != Period.NONE) {
                    valueSamples.addAll(valueAtt.getSamples(start, end));
                } else {
                    DateTime timestampFromLastAlarmLogSample = alarmLogAttribute.getTimestampOfLastSample();
                    if (timestampFromLastAlarmLogSample != null) {
                        valueSamples.addAll(valueAtt.getSamples(timestampFromLastAlarmLogSample.plusSeconds(1), end));
                    } else {
                        DateTime timestampFromLastValueSample = valueAtt.getTimestampOfLastSample();
                        if (timestampFromLastValueSample != null) {
                            valueSamples.addAll(valueAtt.getSamples(timestampFromLastValueSample, end));
                        } else {
                            logger.error("Could not get values for {}:{}", cleanData.getName(), cleanData.getID());
                            continue;
                        }
                    }
                }

                CleanDataObject cleanDataObject = new CleanDataObject(cleanData);
                List<JsonLimitsConfig> cleanDataObjectLimitsConfig = cleanDataObject.getLimitsConfig();
                Double shouldBeValue1Min = null;
                Double shouldBeValue2Min = null;
                Double shouldBeValue1Max = null;
                Double shouldBeValue2Max = null;
                for (JsonLimitsConfig jsonLimitsConfig : cleanDataObjectLimitsConfig) {
                    int index = cleanDataObjectLimitsConfig.indexOf(jsonLimitsConfig);
                    if (index == 0) {
                        try {
                            shouldBeValue1Min = Double.parseDouble(jsonLimitsConfig.getMin());
                            shouldBeValue1Max = Double.parseDouble(jsonLimitsConfig.getMax());
                        } catch (Exception e) {
                            logger.error("Could not parse limit step 1 configuration from object {}:{}", cleanData.getName(), cleanData.getID(), e);
                        }
                    } else if (index == 1) {
                        try {
                            shouldBeValue2Min = Double.parseDouble(jsonLimitsConfig.getMin());
                            shouldBeValue2Max = Double.parseDouble(jsonLimitsConfig.getMax());
                        } catch (Exception e) {
                            logger.error("Could not parse limit step 2 configuration from object {}:{}", cleanData.getName(), cleanData.getID(), e);
                        }
                    }
                }

                JsonDeltaConfig deltaConfig = cleanDataObject.getDeltaConfig();
                Double deltaLevel1 = null;
                Double deltaLevel2 = null;

                if (deltaConfig != null) {
                    try {
                        deltaLevel1 = Double.parseDouble(deltaConfig.getMin());
                    } catch (Exception e) {
                        logger.error("Could not parse delta step 1 setting from object {}:{}", cleanData.getName(), cleanData.getID(), e);
                    }
                    try {
                        deltaLevel2 = Double.parseDouble(deltaConfig.getMax());
                    } catch (Exception e) {
                        logger.error("Could not parse delta step 2 setting from object {}:{}", cleanData.getName(), cleanData.getID(), e);
                    }
                }

                List<JsonGapFillingConfig> gapFillingConfig = cleanDataObject.getGapFillingConfig();

                for (JEVisSample sample : valueSamples) {
                    String note = sample.getNote();

                    if (note.contains(NoteConstants.Limits.LIMIT_STEP1) && (note.contains(NoteConstants.Limits.LIMIT_DEFAULT)
                            || note.contains(NoteConstants.Limits.LIMIT_STATIC) || note.contains(NoteConstants.Limits.LIMIT_AVERAGE)
                            || note.contains(NoteConstants.Limits.LIMIT_MEDIAN) || note.contains(NoteConstants.Limits.LIMIT_INTERPOLATION)
                            || note.contains(NoteConstants.Limits.LIMIT_MIN) || note.contains(NoteConstants.Limits.LIMIT_MAX))) {
                        if (shouldBeValue2Min != null && sample.getValueAsDouble() < shouldBeValue2Min) {
                            activeAlarms.add(new Alarm(cleanData, valueAtt, sample, sample.getTimestamp(), sample.getValueAsDouble(), "<", shouldBeValue2Min, AlarmType.L2, 0));
                        } else if (shouldBeValue2Max != null && sample.getValueAsDouble() > shouldBeValue2Max) {
                            activeAlarms.add(new Alarm(cleanData, valueAtt, sample, sample.getTimestamp(), sample.getValueAsDouble(), ">", shouldBeValue2Max, AlarmType.L2, 0));
                        }
                    } else if (note.contains(NoteConstants.Limits.LIMIT_STEP1)) {
                        if (shouldBeValue1Min != null && sample.getValueAsDouble() < shouldBeValue1Min) {
                            activeAlarms.add(new Alarm(cleanData, valueAtt, sample, sample.getTimestamp(), sample.getValueAsDouble(), "<", shouldBeValue1Min, AlarmType.L1, 0));
                        } else if (shouldBeValue1Max != null && sample.getValueAsDouble() > shouldBeValue1Max) {
                            activeAlarms.add(new Alarm(cleanData, valueAtt, sample, sample.getTimestamp(), sample.getValueAsDouble(), ">", shouldBeValue1Max, AlarmType.L1, 0));
                        }
                    }

                    if (deltaConfig != null) {
                        if (note.contains(NoteConstants.Deltas.DELTA_STEP1) && (note.contains(NoteConstants.Deltas.DELTA_DEFAULT)
                                || note.contains(NoteConstants.Deltas.DELTA_STATIC) || note.contains(NoteConstants.Deltas.DELTA_AVERAGE)
                                || note.contains(NoteConstants.Deltas.DELTA_MEDIAN) || note.contains(NoteConstants.Deltas.DELTA_INTERPOLATION)
                                || note.contains(NoteConstants.Deltas.DELTA_MIN) || note.contains(NoteConstants.Deltas.DELTA_MAX))) {
                            if (deltaLevel2 != null) {
                                try {
                                    String subtext = note.substring(note.lastIndexOf(NoteConstants.Deltas.DELTA_STEP1));
                                    subtext = subtext.replace(NoteConstants.Deltas.DELTA_STEP1 + ",(", "");
                                    subtext = subtext.replace(")", "");
                                    String dValueString = subtext.substring(0, subtext.indexOf(","));
                                    Double dValue = Double.parseDouble(dValueString);
                                    int length = subtext.length();
                                    String deltaValueString = subtext.substring(subtext.indexOf(",") + 1, length);
                                    Double deltaValue = Double.parseDouble(deltaValueString);

                                    activeAlarms.add(new Alarm(cleanData, valueAtt, sample, sample.getTimestamp(), sample.getValueAsDouble(), dValueString, deltaValue, AlarmType.D2, 0));
                                } catch (Exception e) {
                                    logger.error("Could not parse delta step 2 values from object {}:{} and sample {}", cleanData.getName(), cleanData.getID(), sample, e);
                                }

                            }
                        } else if (note.contains(NoteConstants.Deltas.DELTA_STEP1)) {
                            if (deltaLevel1 != null) {
                                try {
                                    String subtext = note.substring(note.lastIndexOf(NoteConstants.Deltas.DELTA_STEP1));
                                    subtext = subtext.replace(NoteConstants.Deltas.DELTA_STEP1 + ",(", "");
                                    subtext = subtext.replace(")", "");
                                    String dValueString = subtext.substring(0, subtext.indexOf(","));
                                    Double dValue = Double.parseDouble(dValueString);
                                    int length = subtext.length();
                                    String deltaValueString = subtext.substring(subtext.indexOf(",") + 1, length);
                                    Double deltaValue = Double.parseDouble(deltaValueString);

                                    activeAlarms.add(new Alarm(cleanData, valueAtt, sample, sample.getTimestamp(), sample.getValueAsDouble(), dValueString, deltaValue, AlarmType.D1, 0));
                                } catch (Exception e) {
                                    logger.error("Could not parse delta step 1 values from object {}:{} and sample {}", cleanData.getName(), cleanData.getID(), sample, e);
                                }
                            }
                        }
                    }

                    if (!gapFillingConfig.isEmpty()) {
                        if (note.contains(NoteConstants.Gap.GAP)) {
                            activeAlarms.add(new Alarm(cleanData, valueAtt, sample, sample.getTimestamp(), sample.getValueAsDouble(), "", null, AlarmType.GAP, 0));
                        }
                    }
                }


                /* all rules of the row, each with its own silent/standby and validity time (before, only the last
                   entry of "Alarm Config" took effect; docs/ALARMS-OUTAGE.md) */
                CleanDataAlarm cleanDataAlarm = new CleanDataAlarm(cleanData);
                List<JEVisSample> alarmLogs = new ArrayList<>();
                if (!valueSamples.isEmpty()) {
                    DateTime from = valueSamples.get(0).getTimestamp();
                    DateTime to = valueSamples.get(valueSamples.size() - 1).getTimestamp();
                    for (AlarmRule rule : cleanDataAlarm.getRules()) {
                        if (rule.getKind() == AlarmRule.Kind.NO_DATA) continue;
                        try {
                            List<AlarmRuleEvaluator.Point> points = new ArrayList<>();
                            if (rule.getAverageMinutes() != null) {
                                addPoints(points, valueAtt.getSamples(from.minusMinutes(rule.getAverageMinutes()), from.minusMillis(1)));
                            }
                            addPoints(points, valueSamples);
                            Function<DateTime, Double> compare = rule.getKind() == AlarmRule.Kind.DYNAMIC
                                    ? comparison(rule, from, to) : null;
                            for (AlarmRuleEvaluator.Hit hit : AlarmRuleEvaluator.evaluate(rule, points, from, compare)) {
                                JEVisSample alarmSample = new VirtualSample(hit.ts, (long) hit.logValue);
                                alarmLogs.add(alarmSample);
                                activeAlarms.add(new Alarm(cleanData, valueAtt, alarmSample, hit.ts, hit.value, hit.operator,
                                        hit.setValue, hit.type, hit.logValue));
                            }
                        } catch (Exception e) {
                            logger.error("Could not evaluate alarm rule '{}' of {}:{}", rule.getName(), cleanData.getName(), cleanData.getID(), e);
                        }
                    }
                }
                if (!alarmLogs.isEmpty()) alarmLogAttribute.addSamples(alarmLogs);
            } catch (Exception e) {
                String parentName = "";
                try {
                    parentName = CommonMethods.getFirstParentalDataObject(cleanData).getName();
                } catch (Exception ex) {
                    logger.error("Could not get parental data object of object {}:{}", cleanData.getName(), cleanData.getID(), ex);
                }
                logger.error("Error while creating alarm for {} / {}:{}", parentName, cleanData.getName(), cleanData.getID(), e);
            }
        }

        return activeAlarms;
    }


    private static void addPoints(List<AlarmRuleEvaluator.Point> points, List<JEVisSample> samples) {
        for (JEVisSample sample : samples) {
            try {
                Double value = sample.getValueAsDouble();
                if (value != null && !value.isNaN()) points.add(new AlarmRuleEvaluator.Point(sample.getTimestamp(), value));
            } catch (Exception e) {
                logger.debug("Skipped sample {}", sample, e);
            }
        }
    }

    /**
     * The comparison value of a DYNAMIC rule at a time stamp: the sample at that time, else the nearest earlier one
     * (up to one day before the first value, as before but without stepping through every second).
     */
    private Function<DateTime, Double> comparison(AlarmRule rule, DateTime from, DateTime to) throws JEVisException {
        TargetHelper th = new TargetHelper(ds, rule.getLimitTarget());
        if (th.getObject().isEmpty() || th.getAttribute().isEmpty() || th.getAttribute().get(0) == null) return ts -> null;
        TreeMap<Long, Double> values = new TreeMap<>();
        for (JEVisSample sample : th.getAttribute().get(0).getSamples(from.minusDays(1), to)) {
            try {
                values.put(sample.getTimestamp().getMillis(), sample.getValueAsDouble());
            } catch (Exception e) {
                logger.debug("Skipped comparison sample {}", sample, e);
            }
        }
        return ts -> {
            Map.Entry<Long, Double> entry = values.floorEntry(ts.getMillis());
            return entry != null ? entry.getValue() : null;
        };
    }

    private List<JEVisObject> getAllCorrespondingCleanDataObjects() throws JEVisException {
        switch (getAlarmConfiguration().getAlarmScope()) {
            case COMPLETE:
                return getCompleteList(null, getAlarmConfiguration().getObject());
            case SELECTED:
                return getListFromSelectedObjects();
            case WITHOUT_SELECTED:
                List<JEVisObject> completeList = getCompleteList(null, getAlarmConfiguration().getObject());
                List<JEVisObject> unselectedList = getListFromSelectedObjects();
                if (unselectedList != null) {
                    completeList.removeAll(unselectedList);
                    return completeList;
                }
                return new ArrayList<>();
            case NONE:
                return new ArrayList<>();
            default:
                return new ArrayList<>();
        }
    }

    private List<JEVisObject> filterForEnabled(List<JEVisObject> completeList) {
        List<JEVisObject> enabledObjects = new ArrayList<>();
        completeList.forEach(jeVisObject -> {
            try {

                JEVisAttribute alarmEnabledAttribute = jeVisObject.getAttribute(ALARM_ENABLED_ATTRIBUTE);

                JEVisSample lastSampleAlarmEnabled = alarmEnabledAttribute.getLatestSample();
                if (lastSampleAlarmEnabled != null) {
                    boolean alarmEnabled = lastSampleAlarmEnabled.getValueAsBoolean();
                    if (alarmEnabled) enabledObjects.add(jeVisObject);
                }
            } catch (Exception e) {
                logger.error("Could not get Attribute.");
            }
        });
        return enabledObjects;
    }

    private List<JEVisObject> getListFromSelectedObjects() {
        return alarmConfiguration.getAlarmObjects();
    }

    private List<JEVisObject> getCompleteList(List<JEVisObject> listObjects, JEVisObject currentObject) throws JEVisException {
        if (listObjects == null) listObjects = new ArrayList<>();


        if (currentObject.getJEVisClass().equals(alarmConfigurationClass)) {
            JEVisObject alarmDir = currentObject.getParents().get(0);
            JEVisObject building = alarmDir.getParents().get(0);

            for (JEVisObject obj : building.getChildren()) {
                if (obj.getJEVisClass().equals(dataDirectoryClass)) getCleanDataChildrenRecursive(listObjects, obj);
            }
        }

        return listObjects;
    }

    private void getCleanDataChildrenRecursive(List<JEVisObject> listObjects, JEVisObject obj) throws JEVisException {
        if (obj.getJEVisClass().equals(cleanDataClass)) listObjects.add(obj);
        else if (obj.getJEVisClass().equals(dataDirectoryClass) || obj.getJEVisClass().equals(rawDataClass) || obj.getJEVisClass().equals(cleanDataClass)) {
            for (JEVisObject child : obj.getChildren()) {
                try {
                    getCleanDataChildrenRecursive(listObjects, child);
                } catch (JEVisException e) {
                    logger.error("Could not get Children.");
                }
            }
        }
    }

    public AlarmConfiguration getAlarmConfiguration() {
        return alarmConfiguration;
    }
}
