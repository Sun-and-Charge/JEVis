/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of Notifier.
 * <p>
 * Notifier is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * Notifier is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * Notifier. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * Notifier is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jenotifier.notifier.Email;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisFile;
import org.jevis.api.JEVisObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 *
 * @author broder
 */
public class EmailServiceProperty {
    private static final Logger logger = LogManager.getLogger(EmailServiceProperty.class);
    private JEVisFile notificationFile;
    private Long mailID;
    private final String NOTIFICATION_FILE = "Notification File";
    private final String REPORT_MAIL_ID = "Notification ID";

    public void initialize(JEVisObject jevisobject) {
        try {
            JEVisAttribute notificationAttribute = jevisobject.getAttribute(NOTIFICATION_FILE);
            notificationFile = notificationAttribute.getLatestSample().getValueAsFile();
            mailID = jevisobject.getAttribute(REPORT_MAIL_ID).getLatestSample().getValueAsLong();
        } catch (JEVisException ex) {
            logger.error(ex);
        }
    }

    public File getNotificationFile() {
        File tmpJar = null;
        try {
            tmpJar = File.createTempFile("notification", ".jar");
            tmpJar.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tmpJar);
            fos.write(notificationFile.getBytes());
        } catch (IOException ex) {
            logger.error(ex);
        }
        return tmpJar;
    }

    public Long getMailID() {
        return mailID;
    }
}
