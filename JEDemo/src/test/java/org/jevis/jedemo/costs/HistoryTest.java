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
package org.jevis.jedemo.costs;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The charge point profile of case F ({@link History#CHARGING_POINT}).
 */
public class HistoryTest {

    static final long[] POINTS = {CostCases.CHARGE_POINT_1, CostCases.CHARGE_POINT_2, CostCases.CHARGE_POINT_3};
    static final Instant FROM = CostCases.CHARGE_FROM.toInstant();
    static final Instant UNTIL = YearMonth.of(2028, 1).atDay(1).atStartOfDay(History.ZONE).toInstant().minusSeconds(900);

    @Test
    public void monthlyTotalsArePlausibleAndDifferPerPoint() {
        StringBuilder table = new StringBuilder();
        double[] means = new double[POINTS.length];
        for (int i = 0; i < POINTS.length; i++) {
            Map<YearMonth, Double> sums = History.monthlySums(History.chargingPoint(POINTS[i], FROM, UNTIL));
            assertEquals(26, sums.size());
            table.append(POINTS[i]).append(':');
            for (Map.Entry<YearMonth, Double> e : sums.entrySet()) {
                double kWh = e.getValue();
                assertTrue(POINTS[i] + " " + e.getKey() + ": " + kWh, kWh >= 150 && kWh <= 250);
                means[i] += kWh / sums.size();
                table.append(String.format(Locale.ROOT, " %s=%.0f", e.getKey(), kWh));
            }
            table.append('\n');
        }
        System.out.println("Charge point profile, kWh per month:\n" + table);
        assertNotEquals(means[0], means[1], 3);
        assertNotEquals(means[1], means[2], 3);
        /* other seeds stay in the range as well */
        for (long seed = 1; seed <= 40; seed++) {
            for (Map.Entry<YearMonth, Double> e : History.monthlySums(History.chargingPoint(seed, FROM, UNTIL)).entrySet()) {
                assertTrue(seed + " " + e.getKey() + ": " + e.getValue(), e.getValue() >= 150 && e.getValue() <= 250);
            }
        }
    }

    @Test
    public void deterministicAndIndependentOfTheRange() {
        List<History.Point> all = History.chargingPoint(CostCases.CHARGE_POINT_1, FROM, UNTIL);
        List<History.Point> again = History.generate(History.CHARGING_POINT, CostCases.CHARGE_POINT_1, FROM, UNTIL);
        assertEquals(all.size(), again.size());
        for (int i = 0; i < all.size(); i++) {
            assertEquals(all.get(i).getTs(), again.get(i).getTs());
            assertEquals(all.get(i).getValue(), again.get(i).getValue(), 0);
        }
        Instant a = ZonedDateTime.of(2026, 10, 5, 7, 30, 0, 0, History.ZONE).toInstant();
        List<History.Point> part = History.chargingPoint(CostCases.CHARGE_POINT_1, a, a.plus(Duration.ofDays(3)));
        int offset = (int) (Duration.between(FROM, a).toMinutes() / 15);
        assertEquals(all.get(offset).getTs(), part.get(0).getTs());
        for (int i = 0; i < part.size(); i++) assertEquals(all.get(offset + i).getValue(), part.get(i).getValue(), 0);

        List<History.Point> other = History.chargingPoint(CostCases.CHARGE_POINT_2, FROM, UNTIL);
        int differ = 0;
        for (int i = 0; i < all.size(); i++) if (all.get(i).getValue() != other.get(i).getValue()) differ++;
        assertTrue("the points differ", differ > 1000);
    }

    @Test
    public void quarterHoursOfCompanyCarCharging() {
        List<History.Point> points = History.chargingPoint(CostCases.CHARGE_POINT_3, FROM, UNTIL);
        /* every quarter hour, also over the DST changes */
        assertEquals(Duration.between(FROM, UNTIL).toMinutes() / 15 + 1, points.size());
        for (int i = 1; i < points.size(); i++) {
            assertEquals(900, Duration.between(points.get(i - 1).getTs(), points.get(i).getTs()).getSeconds());
        }
        double workdays = 0, offDays = 0;
        int full = 0;
        for (History.Point p : points) {
            ZonedDateTime local = p.getTs().atZone(History.ZONE);
            double v = p.getValue();
            assertTrue(p.toString(), v >= 0 && v <= History.CHARGE_QUARTER_KWH + 1e-9);
            if (v == History.CHARGE_QUARTER_KWH) full++;
            int h = local.getHour();
            if (v > 0) assertTrue("charging hours: " + local, h >= 7 && h < 13 || h >= 16 && h < 21);
            if (History.dayOff(local.toLocalDate())) offDays += v;
            else workdays += v;
        }
        assertTrue("mostly at full power", full * History.CHARGE_QUARTER_KWH > 0.6 * (workdays + offDays));
        assertTrue("weekends and holidays rare: " + offDays + " of " + (workdays + offDays), offDays < 0.08 * (workdays + offDays));
        assertEquals(LocalDate.of(2026, 4, 5), History.easter(2026));
        assertTrue(History.dayOff(LocalDate.of(2026, 5, 14)));
        assertFalse(History.dayOff(LocalDate.of(2026, 5, 13)));
    }
}
