/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jevis.commons.json.JsonAlarmConfig;
import org.jevis.commons.json.JsonLimitsConfig;
import org.jevis.commons.json.JsonScheduler;
import org.jevis.commons.json.JsonSchedulerRule;
import org.jevis.commons.json.JsonTools;
import org.joda.time.DateTimeZone;

import java.math.BigDecimal;
import java.util.*;

/**
 * The stored formats of the alarm settings of a clean data row (stage E7), as the Control Center writes them and
 * JEAlarm / the JEDataProcessor read them:
 * <ul>
 * <li>"Limits Configuration" and "Alarm Config": {@code List.toString()} of {@link JsonLimitsConfig} resp.
 * {@link JsonAlarmConfig} ({@code LimitEditor}, {@code AlarmEditor}): {@code [<pretty JSON>, <pretty JSON>]}, all
 * values as strings;</li>
 * <li>silent and standby times: {@link JsonScheduler} ({@code ScheduleEditor}, read by {@code SchedulerHandler}): a
 * time zone and rules with months and weekdays as {@code "1, 2, 3"}, day of month {@code "*"} and start/end hour and
 * minute (the end minute is included, a rule does not cross midnight).</li>
 * </ul>
 * The JSON is handled as trees so keys the web does not know are kept.
 */
final class AlarmFormats {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final List<String> DAYS = Collections.unmodifiableList(Arrays.asList("MON", "TUE", "WED", "THU", "FRI",
            "SAT", "SUN"));
    static final String ALL_MONTHS = "1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12";
    static final String ALL_DAYS_OF_MONTH = "*";
    private static final Set<String> SCHEDULER_KEYS = new HashSet<>(Arrays.asList("timezone", "rules"));
    private static final Set<String> RULE_KEYS = new HashSet<>(Arrays.asList("id", "months", "dayOfMonth", "dayOfWeek",
            "startTimeHours", "startTimeMinutes", "endTimeHours", "endTimeMinutes"));

    private AlarmFormats() {
    }

    // ---------------------------------------------------------------- lists

    /**
     * @return the elements of the stored list (a JSON array or, as the Control Center also reads it, a single object);
     * empty for an empty value; null if it cannot be read
     */
    static List<ObjectNode> parseList(String text) {
        List<ObjectNode> list = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return list;
        try {
            JsonNode node = MAPPER.readTree(text);
            if (node instanceof ObjectNode) {
                list.add((ObjectNode) node);
            } else if (node instanceof ArrayNode) {
                for (JsonNode element : node) {
                    if (!(element instanceof ObjectNode)) return null;
                    list.add((ObjectNode) element);
                }
            } else {
                return null;
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return the list as the Control Center writes it ({@code List.toString()} of the elements, each written with
     * {@link JsonTools#prettyObjectMapper()})
     */
    static String listText(List<ObjectNode> list) throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(JsonTools.prettyObjectMapper().writeValueAsString(list.get(i)));
        }
        return sb.append("]").toString();
    }

    /**
     * @return a new step with all keys of {@link JsonLimitsConfig} (null) in its order
     */
    static ObjectNode newLimitStep() {
        return MAPPER.valueToTree(new JsonLimitsConfig());
    }

    /**
     * @return a new rule with all keys of {@link JsonAlarmConfig} (null) in its order
     */
    static ObjectNode newAlarmRule() {
        return MAPPER.valueToTree(new JsonAlarmConfig());
    }

    // ---------------------------------------------------------------- values

    /**
     * @return the text of the key, null if missing, null or not a value
     */
    static String text(JsonNode node, String key) {
        JsonNode value = node != null ? node.get(key) : null;
        if (value == null || value.isNull() || value.isContainerNode()) return null;
        return value.asText();
    }

    /**
     * @return the number of a stored text, null if empty or no finite number
     */
    static Double number(String text) {
        return ResourceWebEditValues.parseDouble(text);
    }

    /**
     * @return the text of a number as the Control Center's {@code DoubleValidator} gives it ({@code Double.toString},
     * e.g. {@code "10.0"})
     */
    static String doubleText(Double value) {
        return value != null ? Double.toString(value) : null;
    }

    /**
     * @return the text of a number as typed into a text field ({@code "10"}, {@code "2.5"})
     */
    static String plainText(Double value) {
        if (value == null) return null;
        String text = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        return "-0".equals(text) ? "0" : text;
    }

    // ---------------------------------------------------------------- schedules

    /**
     * The simple form of a schedule: weekdays (1 = Monday) and one time range, the end minute included as in the
     * Control Center; a range with {@code from} after {@code to} runs across midnight.
     */
    static final class Simple {
        final TreeSet<Integer> days;
        final int from;
        final int to;
        final String timeZone;

        Simple(Collection<Integer> days, int from, int to, String timeZone) {
            this.days = new TreeSet<>(days);
            this.from = from;
            this.to = to;
            this.timeZone = timeZone;
        }

        /**
         * @return whether days and times are the same (the time zone is not compared)
         */
        boolean sameAs(Simple other) {
            return other != null && days.equals(other.days) && from == other.from && to == other.to;
        }
    }

    static String time(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    /**
     * @return minutes of the day of {@code HH:mm}, null if invalid
     */
    static Integer minutes(String text) {
        if (text == null || !text.matches("\\d{1,2}:\\d{2}")) return null;
        String[] parts = text.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        if (hours > 23 || minutes > 59) return null;
        return hours * 60 + minutes;
    }

    /**
     * @return the numbers of a list as {@code SchedulerHandler} reads it ({@code "1, 2, 3"}), null if it cannot read it
     */
    static List<Integer> numbers(String text) {
        if (text == null) return null;
        List<Integer> result = new ArrayList<>();
        for (String part : text.split(", ")) {
            try {
                result.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return result;
    }

    static String numbersText(Collection<Integer> numbers) {
        StringBuilder sb = new StringBuilder();
        for (Integer number : numbers) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(number);
        }
        return sb.toString();
    }

    private static Integer unit(JsonNode rule, String key, int max) {
        String text = text(rule, key);
        if (text == null || !text.matches("\\d{1,2}")) return null;
        int value = Integer.parseInt(text);
        return value <= max ? value : null;
    }

    /**
     * One rule of a schedule if it is plain: all months, every day of the month, weekdays and a time range that does
     * not cross midnight.
     */
    private static Simple plainRule(JsonNode rule) {
        if (!(rule instanceof ObjectNode)) return null;
        Iterator<String> names = rule.fieldNames();
        while (names.hasNext()) {
            if (!RULE_KEYS.contains(names.next())) return null;
        }
        List<Integer> months = numbers(text(rule, "months"));
        if (months == null || !new HashSet<>(months).equals(new HashSet<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)))) {
            return null;
        }
        String dayOfMonth = text(rule, "dayOfMonth");
        if (dayOfMonth == null || !(dayOfMonth.replace(" ", "").isEmpty() || ALL_DAYS_OF_MONTH.equals(dayOfMonth.replace(" ", "")))) {
            return null;
        }
        List<Integer> days = numbers(text(rule, "dayOfWeek"));
        if (days == null || days.isEmpty()) return null;
        for (Integer day : days) {
            if (day < 1 || day > 7) return null;
        }
        Integer startHours = unit(rule, "startTimeHours", 23);
        Integer startMinutes = unit(rule, "startTimeMinutes", 59);
        Integer endHours = unit(rule, "endTimeHours", 23);
        Integer endMinutes = unit(rule, "endTimeMinutes", 59);
        if (startHours == null || startMinutes == null || endHours == null || endMinutes == null) return null;
        int from = startHours * 60 + startMinutes;
        int to = endHours * 60 + endMinutes;
        if (from > to) return null;
        return new Simple(days, from, to, null);
    }

    private static final int LAST_MINUTE = 23 * 60 + 59;

    /**
     * @param scheduler a stored {@link JsonScheduler}
     * @return its simple form (weekdays and one time range, also across midnight as two rules), null if it has none
     */
    static Simple simple(JsonNode scheduler) {
        if (!(scheduler instanceof ObjectNode)) return null;
        Iterator<String> names = scheduler.fieldNames();
        while (names.hasNext()) {
            if (!SCHEDULER_KEYS.contains(names.next())) return null;
        }
        JsonNode rules = scheduler.get("rules");
        if (!(rules instanceof ArrayNode) || rules.size() < 1 || rules.size() > 2) return null;
        String timeZone = text(scheduler, "timezone");
        if (rules.size() == 1) {
            Simple rule = plainRule(rules.get(0));
            return rule != null ? new Simple(rule.days, rule.from, rule.to, timeZone) : null;
        }
        Simple first = plainRule(rules.get(0));
        Simple second = plainRule(rules.get(1));
        if (first == null || second == null) return null;
        Simple evening = first.to == LAST_MINUTE && second.from == 0 ? first : second;
        Simple morning = evening == first ? second : first;
        if (evening.to != LAST_MINUTE || morning.from != 0 || evening.from <= morning.to) return null;
        if (!morning.days.equals(nextDays(evening.days))) return null;
        return new Simple(evening.days, evening.from, morning.to, timeZone);
    }

    /**
     * @return the days after the days (Sunday is followed by Monday)
     */
    static TreeSet<Integer> nextDays(Collection<Integer> days) {
        TreeSet<Integer> next = new TreeSet<>();
        for (Integer day : days) next.add(day == 7 ? 1 : day + 1);
        return next;
    }

    /**
     * @return the schedule as the Control Center's {@code ScheduleEditor} writes it: one rule, or two rules (until
     * 23:59 and from 00:00 of the next days) for a range across midnight
     */
    static ObjectNode schedulerNode(Simple simple, String timeZone) {
        ObjectNode scheduler = MAPPER.valueToTree(new JsonScheduler());
        scheduler.put("timezone", timeZone);
        ArrayNode rules = MAPPER.createArrayNode();
        if (simple.from <= simple.to) {
            rules.add(ruleNode("0", simple.days, simple.from, simple.to));
        } else {
            rules.add(ruleNode("0", simple.days, simple.from, LAST_MINUTE));
            rules.add(ruleNode("1", nextDays(simple.days), 0, simple.to));
        }
        scheduler.set("rules", rules);
        return scheduler;
    }

    private static ObjectNode ruleNode(String id, Collection<Integer> days, int from, int to) {
        JsonSchedulerRule rule = new JsonSchedulerRule();
        rule.setId(id);
        rule.setMonths(ALL_MONTHS);
        rule.setDayOfMonth(ALL_DAYS_OF_MONTH);
        rule.setDayOfWeek(numbersText(new TreeSet<>(days)));
        rule.setStartTimeHours(String.valueOf(from / 60));
        rule.setStartTimeMinutes(String.valueOf(from % 60));
        rule.setEndTimeHours(String.valueOf(to / 60));
        rule.setEndTimeMinutes(String.valueOf(to % 60));
        return MAPPER.valueToTree(rule);
    }

    /**
     * @return the web form of a stored schedule: {@code {simple: true, days, from, to, timeZone}} or
     * {@code {simple: false, text}}; null without schedule
     */
    static Map<String, Object> scheduleJson(JsonNode scheduler) throws Exception {
        if (scheduler == null || scheduler.isNull()) return null;
        Map<String, Object> json = new LinkedHashMap<>();
        Simple simple = simple(scheduler);
        if (simple != null) {
            List<String> days = new ArrayList<>();
            for (Integer day : simple.days) days.add(DAYS.get(day - 1));
            json.put("simple", true);
            json.put("days", days);
            json.put("from", time(simple.from));
            json.put("to", time(simple.to));
            json.put("timeZone", simple.timeZone);
        } else {
            json.put("simple", false);
            json.put("text", MAPPER.writeValueAsString(scheduler));
        }
        return json;
    }

    /**
     * @return whether the id is a time zone Joda knows ({@code SchedulerHandler} reads it with
     * {@code DateTimeZone.forID})
     */
    static boolean validZone(String id) {
        if (id == null || id.trim().isEmpty()) return false;
        try {
            DateTimeZone.forID(id);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Reads a schedule sent by the web.
     *
     * @param sent        the value sent ({@code null} node: no schedule)
     * @param stored      the stored schedule, null if none
     * @param defaultZone time zone for a new schedule (the stored one is kept if valid)
     * @return the schedule to store (the stored node itself if unchanged), null for none; problems are added to the
     * messages
     */
    static JsonNode schedule(JsonNode sent, JsonNode stored, String defaultZone, String field, List<String> messages) {
        if (sent == null || sent.isNull()) return null;
        if (!sent.isObject()) {
            messages.add(field + ": object expected");
            return stored;
        }
        JsonNode simpleNode = sent.get("simple");
        if (simpleNode == null || !simpleNode.isBoolean()) {
            messages.add(field + ": simple missing");
            return stored;
        }
        if (!simpleNode.asBoolean()) {
            JsonNode text = sent.get("text");
            JsonNode tree = null;
            try {
                tree = text != null && text.isTextual() ? MAPPER.readTree(text.asText()) : null;
            } catch (Exception e) {
                tree = null;
            }
            if (stored != null && stored.equals(tree)) return stored;
            messages.add(field + ": this schedule can only be changed in the Control Center");
            return stored;
        }
        JsonNode daysNode = sent.get("days");
        TreeSet<Integer> days = new TreeSet<>();
        if (daysNode == null || !daysNode.isArray() || daysNode.size() == 0) {
            messages.add(field + ": at least one day expected");
            return stored;
        }
        for (JsonNode day : daysNode) {
            int index = day.isTextual() ? DAYS.indexOf(day.asText()) : -1;
            if (index < 0 || !days.add(index + 1)) {
                messages.add(field + ": invalid day " + day);
                return stored;
            }
        }
        Integer from = minutes(text(sent, "from"));
        Integer to = minutes(text(sent, "to"));
        if (from == null || to == null) {
            messages.add(field + ": from and to as HH:mm expected");
            return stored;
        }
        Simple wanted = new Simple(days, from, to, null);
        Simple current = simple(stored);
        if (current != null && current.sameAs(wanted)) return stored;
        String storedZone = text(stored, "timezone");
        String zone = validZone(storedZone) ? storedZone : defaultZone;
        return schedulerNode(wanted, zone);
    }
}
