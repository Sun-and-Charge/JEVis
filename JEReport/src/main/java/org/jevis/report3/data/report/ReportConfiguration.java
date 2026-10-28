/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEReport.
 * <p>
 * JEReport is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEReport is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEReport. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEReport is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.report3.data.report;

/**
 *
 * @author broder
 */
public class ReportConfiguration {

    //attributes
    //date format
    //public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    //notification configs
    public static final Long REPORT_DRIVER_OBJECT_ID = 4158l;
    public static final String NOTIFICATION_DRIVER = "org.jevis.jenotifier.notifier.Email.EmailNotificationDriver";
    public static final String NOTIFICATION = "org.jevis.jenotifier.notifier.Email.EmailNotification";

}
