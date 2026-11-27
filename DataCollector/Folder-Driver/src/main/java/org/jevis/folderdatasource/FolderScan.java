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

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the files of a local folder channel: the channel's "Path" is a regular expression on the full path with "/"
 * as separator (as for sFTP, e.g. {@code /srv/jevis/inbox/rechnungen/.*\.(pdf|xml)}); the folder searched is the part
 * before the first "(" or wildcard up to its last "/". Only files changed after the last readout, oldest first.
 */
final class FolderScan {

    private FolderScan() {
    }

    static String root(String regex) {
        int cut = regex.length();
        for (char c : new char[]{'(', '.', '*', '[', '+', '?', '\\', '^', '$', '|', '{'}) {
            int i = regex.indexOf(c);
            if (i >= 0 && i < cut) cut = i;
        }
        String fixed = regex.substring(0, cut);
        int slash = fixed.lastIndexOf('/');
        return slash > 0 ? fixed.substring(0, slash) : "/";
    }

    /** path with "/" separators (Windows paths too) */
    static String unix(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    static List<Path> find(String regex, long changedAfterMillis) throws IOException {
        Pattern pattern = Pattern.compile(regex);
        Path root = Paths.get(root(regex));
        if (!Files.isDirectory(root)) throw new NoSuchFileException(root.toString(), null, "folder of the channel path does not exist");
        Map<Path, Long> found = new HashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    long modified = Files.readAttributes(p, BasicFileAttributes.class).lastModifiedTime().toMillis();
                    if (modified > changedAfterMillis && pattern.matcher(unix(p)).matches()) found.put(p, modified);
                } catch (IOException e) {
                    /* vanished meanwhile */
                }
            });
        }
        List<Path> list = new ArrayList<>(found.keySet());
        list.sort(Comparator.comparingLong((Path p) -> found.get(p)).thenComparing(Path::toString));
        return list;
    }

    /** moves the file into the folder, with a number appended when the name exists there */
    static Path moveTo(Path file, Path folder) throws IOException {
        Files.createDirectories(folder);
        Path target = folder.resolve(file.getFileName());
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        for (int n = 1; Files.exists(target); n++) {
            target = folder.resolve(dot > 0 ? name.substring(0, dot) + "-" + n + name.substring(dot) : name + "-" + n);
        }
        return Files.move(file, target);
    }
}
