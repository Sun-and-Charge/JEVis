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
package org.jevis.jenotifier.exporter;

import org.jevis.api.*;
import org.jevis.jenotifier.config.JENotifierConfig;
import org.jevis.jenotifier.mode.SendNotification;
import org.jevis.jenotifier.notifier.Email.EmailNotification;
import org.jevis.jenotifier.notifier.Notification;

import java.io.File;
import java.util.List;

public abstract class Export {

    public static String TYPE_ENABLED = "Enabled";
    public static String TYPE_TIMEZONE = "Time Zone";
    public static String CLASS_NAME = "CSV Export";

    protected JEVisAttribute attEnabled;
    protected JEVisAttribute attTimeZone;
    protected boolean isEnabled = false;

    protected JEVisObject exportObject;
    protected JENotifierConfig jeNotifierConfig;

    public Export(JENotifierConfig jeNotifierConfig, JEVisObject object) {
        this.exportObject = object;
        this.jeNotifierConfig = jeNotifierConfig;
//        init();
    }

    public void init() {
        try {
            attEnabled = exportObject.getAttribute(TYPE_ENABLED);
            isEnabled = attEnabled.getLatestSample().getValueAsBoolean();
        } catch (JEVisException e) {
            e.printStackTrace();
        }

        try {
            attTimeZone = exportObject.getAttribute(TYPE_TIMEZONE);
        } catch (JEVisException e) {
            e.printStackTrace();
        }


        try {
            initSettings();
        } catch (Exception ex) {

        }
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    abstract void initSettings() throws Exception;

    abstract List<File> getExportFiles();

    public abstract void executeExport() throws Exception;

    public void sendNotification() {
        try {
            JEVisClass mailClass = exportObject.getDataSource().getJEVisClass(EmailNotification._type);

            exportObject.getChildren(mailClass, true).forEach(object -> {
                try {

                    //System.out.println("send email: " + object);
                    Notification nofi = new EmailNotification();
                    JEVisFile jeVisFile = null;

//                    for (File exportFile : getExportFiles()) {
//                        try {
//                            jeVisFile = new JEVisFileImp("test.csv", exportFile);
//                        } catch (Exception ex) {
//                            ex.printStackTrace();
//                        }
//                    }
//                    ((EmailNotification) nofi).setMessage("Test Message hhh");
//                    nofi.setNotificationObject(object, jeVisFile);
                    nofi.setNotificationObject(object);


                    for (File exportFile : getExportFiles()) {
                        ((EmailNotification) nofi).setAttachmentsAsFile(exportFile);
                    }


//                    System.out.println("notification: " + nofi);
//                    System.out.println("notification driver: " + jeNotifierConfig.getDefaultEmailNotificationDriver());
                    SendNotification sn = new SendNotification(nofi, jeNotifierConfig.getDefaultEmailNotificationDriver(), "");
                    sn.run();
                    sn = null;
                    if (nofi.isSendSuccessfully()) {
                        setOnSuccess();
                    }
                    cleanUp();

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });


        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public long getObjectID() {
        return exportObject.getID();
    }

    public JEVisObject getExportObject() {
        return exportObject;
    }

    public abstract void setOnSuccess();

    public abstract void cleanUp();

    public abstract boolean hasNewData();

    @Override
    public String toString() {
        if (exportObject != null) {
            return String.format("Export{ id: %s, name: %s}", exportObject.getID(), exportObject.getName());
        } else {
            return "Export{null}";
        }

    }
}
