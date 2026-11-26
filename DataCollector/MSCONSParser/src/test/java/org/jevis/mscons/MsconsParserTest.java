/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of MSCONSParser.
 * <p>
 * MSCONSParser is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * MSCONSParser is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * MSCONSParser. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * MSCONSParser is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.mscons;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MsconsParserTest {

    @Test
    void sampleFileHasOneLocationWithRegisterAndQuarterHours() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/TL-example.mscons.txt")) {
            MsconsParser parser = new MsconsParser(in);
            assertTrue(parser.parse());
            MsconsPojo pojo = parser.getMsconsPojo();
            assertEquals(1, pojo.getMesslokation().size());
            Messlokation location = pojo.getMesslokation().get(0);
            assertEquals("LU00000604360ELEC0000000000022455", location.getMesslokationNumber());
            List<MsconsSample> samples = location.getSampleList();
            assertEquals(2980, samples.size());
            MsconsSample first = samples.get(0);
            assertEquals("1-1:1.29.0", first.getObis());
            assertEquals("220", first.getQualifier());
            /* DTM format 303 with the offset "?+02": 2018-10-01 00:00 local summer time */
            assertEquals(new DateTime(2018, 9, 30, 22, 0, DateTimeZone.UTC).getMillis(), first.getStart().getMillis());
            assertEquals(15, (first.getEnd().getMillis() - first.getStart().getMillis()) / 60000);
        }
    }

    @Test
    void registersSubstituteValuesAndReleaseCharacter() {
        String message = "UNA:+.? '"
                + "UNB+UNOC:3+9900000000001:500+9900000000002:500+261101:0800+REF1++TL'"
                + "UNH+1+MSCONS:D:04B:UN:2.4c'"
                + "BGM+7+DOC?'1+9'"
                + "LOC+172+DE0001234567890000000000000000001::89'"
                + "LIN+1'PIA+5+1-1?:1.29.0:SRW'"
                + "QTY+220:1.5'DTM+163:202611010000?+01:303'DTM+164:202611010015?+01:303'"
                + "QTY+67:2,25'DTM+163:202611010015?+01:303'DTM+164:202611010030?+01:303'"
                + "QTY+20:9'DTM+163:202611010030?+01:303'DTM+164:202611010045?+01:303'"
                + "LIN+2'PIA+5+1-1?:2.29.0:SRW'"
                + "QTY+220:0.5'DTM+163:202611010000?+01:303'DTM+164:202611010015?+01:303'"
                + "UNT+15+1'UNZ+1+REF1'";
        MsconsParser parser = new MsconsParser(new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
        assertTrue(parser.parse());
        List<MsconsSample> samples = parser.getMsconsPojo().getMesslokation().get(0).getSampleList();
        /* QTY+20 (not usable) is skipped */
        assertEquals(3, samples.size());
        assertEquals("1-1:1.29.0", samples.get(0).getObis());
        assertEquals("67", samples.get(1).getQualifier());
        assertEquals(2.25, samples.get(1).getQuantity(), 1e-9);
        assertEquals("1-1:2.29.0", samples.get(2).getObis());
        /* the escaped apostrophe in the BGM document number does not split the segment */
        assertEquals("DOC'1", parser.getMsconsPojo().getMessageHeader().getMessageIdentification().replace("?'", "'"));
    }
}
