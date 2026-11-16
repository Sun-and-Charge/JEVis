/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo;

import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.appointment.MailState;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;

import java.util.regex.Pattern;

/**
 * Demo reading rounds send no mails: the "Mail State" of each round is written so that the JENotifier
 * ({@code RoundMailLogic.pendingMail}) never finds a mail to send, now and when the next due date comes.
 * <p>
 * The JENotifier sends only the latest applicable kind for the round's {@code due} and checks only that kind's field
 * against {@code due}: ORDER ({@code dueMailSentFor}) from {@code due} until {@code due + min(Reminder Days,
 * Escalation Days)}, REMINDER ({@code reminderSentFor}) until {@code due + Escalation Days}, ESCALATION
 * ({@code escalationSentFor}) until the next due date. Each field holds one date, so it is set to {@code due} while
 * its kind can still apply for {@code due} and to {@code nextDue} in advance as soon as it cannot any more. Then no
 * kind is pending on any day, also on the next due date before the next cycle of the JEDemo job. Only an escalation
 * after 0 days could apply on the next due date itself; the demo rounds use 7.
 * <p>
 * {@code due}/{@code nextDue} are computed like the web service ({@code ResourceWebReadingRounds.schedule}):
 * {@code due} = the latest start + n × period not after today, {@code nextDue} = start + (n + 1) × period; before the
 * start {@code due} = null and every field holds the start date.
 */
public final class RoundMailMarker {

    public static final String CLASS_NAME = "Reading Round";
    public static final String A_MAIL_STATE = "Mail State";
    public static final String A_START_DATE = "Start Date";
    public static final String A_PERIOD = "Period";
    public static final String A_REMINDER_DAYS = "Reminder Days";
    public static final String A_ESCALATION_DAYS = "Escalation Days";
    /**
     * defaults of the web service when the attribute is empty
     */
    static final int DEFAULT_REMINDER_DAYS = 3;
    static final int DEFAULT_ESCALATION_DAYS = 7;
    /**
     * the time zone of the demo sites (the web service uses the site's)
     */
    public static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private RoundMailMarker() {
    }

    /**
     * The order of a period: {@code due} (null before the start) and {@code nextDue}.
     */
    public static final class Schedule {
        public final LocalDate due;
        public final LocalDate nextDue;

        Schedule(LocalDate due, LocalDate nextDue) {
            this.due = due;
            this.nextDue = nextDue;
        }
    }

    /**
     * @return the schedule as the web service computes it; null without start date or period
     */
    public static Schedule schedule(LocalDate start, Period period, LocalDate today) {
        if (start == null || period == null || today == null) return null;
        if (today.isBefore(start)) return new Schedule(null, start);
        long approxDays = Math.max(1, period.getYears() * 365L + period.getMonths() * 30L + period.getWeeks() * 7L + period.getDays());
        long days = new org.joda.time.Duration(start.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                today.toDateTimeAtStartOfDay(DateTimeZone.UTC)).getStandardDays();
        int n = (int) Math.max(0, Math.min(Integer.MAX_VALUE / 2, days / approxDays - 1));
        while (n > 0 && start.plus(period.multipliedBy(n)).isAfter(today)) n--;
        while (!start.plus(period.multipliedBy(n + 1)).isAfter(today)) n++;
        return new Schedule(start.plus(period.multipliedBy(n)), start.plus(period.multipliedBy(n + 1)));
    }

    /**
     * @return {dueMailSentFor, reminderSentFor, escalationSentFor} so that no mail is pending today nor on the next
     * due date; null without schedule
     */
    public static String[] marks(LocalDate start, Period period, int reminderDays, int escalationDays, LocalDate today) {
        Schedule s = schedule(start, period, today);
        if (s == null) return null;
        String next = s.nextDue.toString();
        if (s.due == null) return new String[]{next, next, next};
        String due = s.due.toString();
        int reminder = Math.max(0, reminderDays);
        int escalation = Math.max(0, escalationDays);
        String order = !today.isBefore(s.due.plusDays(Math.min(reminder, escalation))) ? next : due;
        String remind = !today.isBefore(s.due.plusDays(escalation)) ? next : due;
        return new String[]{order, remind, due};
    }

    /**
     * The "Mail State" a demo round should hold: the marks of {@link #marks} and no last mail ({@code lastMail},
     * {@code lastMailKind} null); other fields (e.g. {@code lastError}) are kept.
     *
     * @param current the current "Mail State" JSON (null or empty if none)
     * @return the JSON to write, or null if the current state already is the wanted one or the round has no valid
     * start date or period
     */
    public static String update(String current, String startDate, String period, String reminderDays, String escalationDays,
                                LocalDate today) {
        String[] marks = marks(date(startDate), period(period), days(reminderDays, DEFAULT_REMINDER_DAYS),
                days(escalationDays, DEFAULT_ESCALATION_DAYS), today);
        if (marks == null) return null;
        MailState before = EntryFormat.fromJson(current, MailState.class);
        MailState state = EntryFormat.fromJson(current, MailState.class);
        if (state == null) state = new MailState();
        state.setDueMailSentFor(marks[0]);
        state.setReminderSentFor(marks[1]);
        state.setEscalationSentFor(marks[2]);
        state.setLastMail(null);
        state.setLastMailKind(null);
        return state.equals(before) ? null : EntryFormat.toJson(state);
    }

    /**
     * @return the date of a {@code yyyy-MM-dd} text, null if missing or invalid
     */
    static LocalDate date(String text) {
        if (text == null || !DATE.matcher(text.trim()).matches()) return null;
        try {
            return LocalDate.parse(text.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @return the positive ISO period of the text, null otherwise (as the web service)
     */
    static Period period(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        Period period;
        try {
            period = new Period(text.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        boolean positive = false;
        for (int value : period.getValues()) {
            if (value < 0) return null;
            if (value > 0) positive = true;
        }
        return positive ? period : null;
    }

    static int days(String text, int fallback) {
        if (text == null || text.trim().isEmpty()) return fallback;
        try {
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.round(Double.parseDouble(text.trim()))));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
