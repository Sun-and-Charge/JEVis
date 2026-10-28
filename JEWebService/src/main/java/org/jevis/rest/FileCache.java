/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 *
 * @author fs
 */
public class FileCache {

    private static final Logger logger = LogManager.getLogger(FileCache.class);
    public static final String CACH_PATH = System.getProperty("java.io.tmpdir") + "/JEWebService";

    public final static String CLASS_ICON_FILE = CACH_PATH + "/allIcons.zip";
    public final static String CLASS_JSON = CACH_PATH + "/Classes.json";

    public static File getClassFile() {
        cratePath();
        return new File(CLASS_JSON);
    }

    public static File getClassIconFile() {
        cratePath();
        File tmpZipFile = new File(CLASS_ICON_FILE);
        return tmpZipFile;
    }

    public static void deleteClassFile() {
        try {
            File tmpZipFile = new File(CLASS_JSON);
            if (tmpZipFile.exists()) {
                tmpZipFile.delete();
            }else{
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.catching(ex);
        }
    }

    public static void deleteClassIconFile() {
        cratePath();
        try {
            File tmpZipFile = new File(CLASS_ICON_FILE);
            if (tmpZipFile.exists()) {
                tmpZipFile.delete();
            }
        } catch (Exception ex) {
            logger.catching(ex);
        }
    }

    public static void deleteClassCachFiles() {
        deleteClassFile();
        deleteClassIconFile();
    }

    private static void cratePath() {
        File tmpfolder = new File(CACH_PATH);
        if (!tmpfolder.exists()) {
            tmpfolder.mkdirs();
        }
    }

}
