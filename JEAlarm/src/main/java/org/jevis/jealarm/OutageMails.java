/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEAlarm.
 * <p>
 * JEAlarm is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEAlarm is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEAlarm. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEAlarm is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jealarm;

import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.alarm.AlarmType;
import org.jevis.commons.alarm.OutageEvent;
import org.jevis.commons.alarm.OutageLogic;
import org.jevis.commons.alarm.OutageState;
import org.jevis.commons.json.JsonAlarm;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * HTML mails of the data outage check and the daily summary (docs/ALARMS-OUTAGE.md); German labels as the alarm table.
 */
final class OutageMails {

    static final int MAX_ROWS = 50;
    private static final String TABLE = "border-collapse:collapse;font-family:Arial,sans-serif;font-size:13px;";
    private static final String TH = "text-align:left;padding:4px 8px;background:#1a2a45;color:#fff;";
    private static final String TD = "padding:4px 8px;border-bottom:1px solid #d9e4e6;";

    private OutageMails() {
    }

    static String startSubject(String configuration, int count) {
        return "Datenausfall: " + configuration + " (" + count + ")";
    }

    static String summarySubject(String configuration) {
        return "Tageszusammenfassung Alarme: " + configuration;
    }

    static String startHtml(JEVisDataSource ds, List<OutageState.Open> started, DateTimeZone zone) {
        StringBuilder sb = new StringBuilder("<html><body style=\"font-family:Arial,sans-serif\">");
        sb.append("<p>Für folgende Datenreihen bzw. Datenquellen sind keine neuen Werte mehr eingegangen:</p>");
        openTable(sb, started, ds, zone);
        sb.append("<p>Endet ein Ausfall, erscheint dies in der Tageszusammenfassung und in der Alarmliste der Web-App.</p>");
        return sb.append("</body></html>").toString();
    }

    static String summaryHtml(JEVisDataSource ds, List<JsonAlarm> alarms, List<OutageEvent> events, List<OutageState.Open> open,
                              DateTimeZone zone) {
        StringBuilder sb = new StringBuilder("<html><body style=\"font-family:Arial,sans-serif\">");
        sb.append("<h2 style=\"font-size:16px\">Alarme und Datenausfälle der letzten 24 Stunden</h2>");
        if (!open.isEmpty()) {
            sb.append("<h3 style=\"font-size:14px\">Noch offene Ausfälle (").append(open.size()).append(")</h3>");
            openTable(sb, open, ds, zone);
        }
        long ended = events.stream().filter(e -> OutageEvent.END.equals(e.event)).count();
        if (ended > 0) {
            sb.append("<h3 style=\"font-size:14px\">Beendete Ausfälle (").append(ended).append(")</h3>");
            sb.append("<table style=\"").append(TABLE).append("\"><tr>");
            header(sb, "Objekt", "Art", "Ohne Daten seit", "Wieder Daten ab", "Ergebnis");
            int n = 0;
            for (OutageEvent e : events) {
                if (!OutageEvent.END.equals(e.event) || n++ >= MAX_ROWS) continue;
                sb.append("<tr>");
                cell(sb, name(ds, e.object));
                cell(sb, kind(e.kind));
                cell(sb, time(e.since, zone));
                cell(sb, time(e.resumed, zone));
                cell(sb, e.resumed == null ? "nicht mehr überwacht" : Boolean.TRUE.equals(e.backfilled) ? "verspätet geliefert" : "Lücke bleibt");
                sb.append("</tr>");
            }
            sb.append("</table>");
        }
        if (!alarms.isEmpty()) {
            sb.append("<h3 style=\"font-size:14px\">Grenzwertalarme (").append(alarms.size()).append(")</h3>");
            sb.append("<table style=\"").append(TABLE).append("\"><tr>");
            header(sb, "Datenreihe", "Zeit", "Wert", "", "Grenze", "Art");
            NumberFormat nf = NumberFormat.getInstance(Locale.GERMANY);
            nf.setMaximumFractionDigits(2);
            int n = 0;
            for (JsonAlarm a : alarms) {
                if (n++ >= MAX_ROWS) break;
                sb.append("<tr>");
                cell(sb, a.getObject() != null ? name(ds, a.getObject()) : "");
                cell(sb, time(a.getTimeStamp(), zone));
                cell(sb, a.getIsValue() != null ? nf.format(a.getIsValue()) : "");
                cell(sb, a.getOperator() != null ? a.getOperator() : "");
                cell(sb, a.getShouldBeValue() != null ? nf.format(a.getShouldBeValue()) : "");
                cell(sb, type(a.getAlarmType()));
                sb.append("</tr>");
            }
            sb.append("</table>");
            if (alarms.size() > MAX_ROWS) sb.append("<p>… und ").append(alarms.size() - MAX_ROWS).append(" weitere.</p>");
        }
        return sb.append("</body></html>").toString();
    }

    private static void openTable(StringBuilder sb, List<OutageState.Open> list, JEVisDataSource ds, DateTimeZone zone) {
        sb.append("<table style=\"").append(TABLE).append("\"><tr>");
        header(sb, "Objekt", "Art", "Letzter Wert", "Erlaubt", "Ursache");
        int n = 0;
        for (OutageState.Open o : list) {
            if (n++ >= MAX_ROWS) break;
            sb.append("<tr>");
            cell(sb, name(ds, o.object));
            cell(sb, kind(o.kind));
            cell(sb, o.since != null ? time(o.since, zone) : "noch nie");
            cell(sb, duration(o.minutes));
            cell(sb, o.cause != null ? "Datenquelle " + name(ds, o.cause) : "");
            sb.append("</tr>");
        }
        sb.append("</table>");
    }

    static String kind(String kind) {
        return OutageMonitor.KIND_SOURCE.equals(kind) ? "Übertragungsstörung (Datenquelle)" : "Datenausfall";
    }

    static String type(AlarmType type) {
        if (type == null) return "";
        switch (type) {
            case STATIC:
                return "fester Grenzwert";
            case DYNAMIC:
                return "Vergleichsreihe";
            case GAP:
                return "Lücke gefüllt";
            case D1:
            case D2:
                return "Sprung " + (type == AlarmType.D1 ? "Stufe 1" : "Stufe 2");
            default:
                return type.name();
        }
    }

    static String duration(int minutes) {
        if (minutes % 1440 == 0) return (minutes / 1440) + (minutes == 1440 ? " Tag" : " Tage");
        if (minutes % 60 == 0) return (minutes / 60) + (minutes == 60 ? " Stunde" : " Stunden");
        return minutes + " Minuten";
    }

    static String time(String iso, DateTimeZone zone) {
        DateTime t = OutageLogic.parse(iso);
        return t != null ? t.withZone(zone).toString("dd.MM.yyyy HH:mm") : "";
    }

    private static String name(JEVisDataSource ds, long id) {
        try {
            JEVisObject o = ds.getObject(id);
            if (o == null) return "#" + id;
            String name = o.getName();
            /* a clean data row is shown with its raw data row ("Strom - Halle 3 / Bereinigte Daten") */
            if (!o.getParents().isEmpty() && "Clean Data".equals(o.getJEVisClassName())) name = o.getParents().get(0).getName() + " / " + name;
            return escape(name);
        } catch (Exception e) {
            return "#" + id;
        }
    }

    private static void header(StringBuilder sb, String... names) {
        for (String n : names) sb.append("<th style=\"").append(TH).append("\">").append(n).append("</th>");
        sb.append("</tr>");
    }

    private static void cell(StringBuilder sb, String text) {
        sb.append("<td style=\"").append(TD).append("\">").append(text).append("</td>");
    }

    static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
