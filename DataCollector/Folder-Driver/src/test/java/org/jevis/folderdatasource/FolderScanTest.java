/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of FolderDataSource.
 * <p>
 * FolderDataSource is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * FolderDataSource is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * FolderDataSource. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * FolderDataSource is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.folderdatasource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Local folder channel: path regex, last readout, move. */
class FolderScanTest {

    @Test
    void rootOfThePathRegex() {
        assertEquals("/srv/inbox/rechnungen", FolderScan.root("/srv/inbox/rechnungen/.*\\.(pdf|xml)"));
        assertEquals("/srv/inbox", FolderScan.root("/srv/inbox/(a|b)/x.csv"));
        assertEquals("/", FolderScan.root("/.*"));
        assertEquals("C:/inbox", FolderScan.root("C:/inbox/.*"));
    }

    @Test
    void findsMatchingFilesChangedAfterTheLastReadoutOldestFirst(@TempDir Path dir) throws Exception {
        Path sub = Files.createDirectories(dir.resolve("rechnungen/2026"));
        Path a = Files.write(sub.resolve("a.pdf"), "A".getBytes());
        Path b = Files.write(dir.resolve("rechnungen/b.xml"), "B".getBytes());
        Path c = Files.write(dir.resolve("rechnungen/c.txt"), "C".getBytes());
        Path old = Files.write(dir.resolve("rechnungen/old.pdf"), "O".getBytes());
        Files.setLastModifiedTime(old, FileTime.fromMillis(1000));
        Files.setLastModifiedTime(b, FileTime.fromMillis(5_000_000));
        Files.setLastModifiedTime(a, FileTime.fromMillis(9_000_000));
        /* the temporary folder has no regex characters in its path; quoting it would make the whole drive the root */
        String regex = FolderScan.unix(dir.resolve("rechnungen")) + "/.*\\.(pdf|xml)";
        List<Path> found = FolderScan.find(regex, 2000);
        assertEquals(2, found.size());
        assertEquals(b, found.get(0));
        assertEquals(a, found.get(1));
        assertFalse(found.contains(c));
        assertThrows(NoSuchFileException.class, () -> FolderScan.find(FolderScan.unix(dir) + "/fehlt/.*", 0));
    }

    @Test
    void movesWithoutOverwriting(@TempDir Path dir) throws Exception {
        Path done = dir.resolve("erledigt");
        Path first = FolderScan.moveTo(Files.write(dir.resolve("r.pdf"), "1".getBytes()), done);
        Path second = FolderScan.moveTo(Files.write(dir.resolve("r.pdf"), "2".getBytes()), done);
        assertEquals("r.pdf", first.getFileName().toString());
        assertEquals("r-1.pdf", second.getFileName().toString());
        assertEquals("2", new String(Files.readAllBytes(second)));
    }
}
