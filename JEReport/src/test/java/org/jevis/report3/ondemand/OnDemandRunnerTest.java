/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEReport.
 * <p>
 * JEReport is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEReport is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEReport. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEReport is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.report3.ondemand;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** R04: which requests the runner renders */
public class OnDemandRunnerTest {

    private static final DateTimeZone Z = DateTimeZone.forID("Europe/Berlin");
    private static final DateTime NOW = new DateTime(2026, 11, 27, 10, 0, Z);

    @Test
    public void requestAsWrittenByTheWebService() {
        OnDemandRunner.Request r = OnDemandRunner.request("{\"id\":\"a\",\"start\":\"2026-10-01T00:00:00.000+02:00\",\"requestedBy\":7,\"requestedByName\":\"Erika Muster\",\"mail\":true}", NOW);
        assertNotNull(r);
        assertEquals("a", r.id);
        assertEquals(new DateTime(2026, 10, 1, 0, 0, Z).getMillis(), r.start.getMillis());
        assertEquals(7L, r.requestedBy);
        assertTrue(r.mail);
        assertEquals(Collections.emptyList(), r.mailTo);
        assertEquals(Collections.singletonList("erika@example.com"),
                OnDemandRunner.request("{\"id\":\"b\",\"start\":\"2026-10-01T00:00:00.000+02:00\",\"mail\":true,\"mailTo\":[\"erika@example.com\",\"kaputt\"]}", NOW).mailTo);
        assertNull(OnDemandRunner.request("{\"id\":\"a\"}", NOW));
        assertNull(OnDemandRunner.request("kein JSON", NOW));
    }

    @Test
    public void pendingRequestsOldestFirst() {
        DateTime start = new DateTime(2026, 10, 1, 0, 0, Z);
        List<OnDemandRunner.Request> requests = Arrays.asList(
                OnDemandRunner.request("{\"id\":\"new\",\"start\":\"" + start + "\"}", NOW.minusMinutes(1)),
                OnDemandRunner.request("{\"id\":\"older\",\"start\":\"" + start + "\"}", NOW.minusMinutes(5)),
                OnDemandRunner.request("{\"id\":\"done\",\"start\":\"" + start + "\"}", NOW.minusMinutes(10)),
                OnDemandRunner.request("{\"id\":\"running\",\"start\":\"" + start + "\"}", NOW.minusMinutes(20)),
                OnDemandRunner.request("{\"id\":\"stale\",\"start\":\"" + start + "\"}", NOW.minusHours(2)),
                OnDemandRunner.request("{\"id\":\"expired\",\"start\":\"" + start + "\"}", NOW.minusDays(8)));
        Map<String, Object[]> statuses = new HashMap<>();
        statuses.put("done", new Object[]{OnDemandRunner.DONE, NOW.minusMinutes(9)});
        statuses.put("running", new Object[]{OnDemandRunner.RUNNING, NOW.minusMinutes(19)});
        /* still running after 30 minutes: interrupted by a restart, run again */
        statuses.put("stale", new Object[]{OnDemandRunner.RUNNING, NOW.minusMinutes(110)});

        List<String> ids = new ArrayList<>();
        for (OnDemandRunner.Request r : OnDemandRunner.pending(requests, statuses, NOW)) ids.add(r.id);
        assertEquals(Arrays.asList("stale", "older", "new"), ids);
    }
}
