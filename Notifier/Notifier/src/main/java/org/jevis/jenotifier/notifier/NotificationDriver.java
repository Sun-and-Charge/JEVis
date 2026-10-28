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
package org.jevis.jenotifier.notifier;

import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;

import java.util.List;

/**
 *
 * @author gf
 */
public interface NotificationDriver {

    /**
     * Returns the type of driver.
     *
     * @return
     */
    String getDriverType();

    /**
     * Send the Notification. If the notification is successully sent, returns
     * true. If not, returns false.
     *
     * @param jenoti
     * @return
     */
    boolean sendNotification(Notification jenoti);

    /**
     * Send the Notification. If the notification is successully sent, returns
     * true. If not, returns false.
     *
     * @param jenoti
     * @return
     */
    boolean sendNotification(Notification jenoti, String customMessage);

    /**
     * Check, whether the notification driver is configured to send the notification.
     *
     * @return
     */
    boolean isDriverConfigured();

    /**
     * Check, whether Notification and Notification Driver are corresponding.
     *
     * @param jenoti
     * @return
     */
    boolean isSupported(Notification jenoti);

    /**
     * Set all global variables with the import parameters in database read by
     * JEVisObject.
     *
     * @param notiObj
     * @throws JEVisException
     */
    void setNotificationDriverObject(JEVisObject notiObj) throws JEVisException;

    /**
     *To store the sent Time in JEConfig(database)
     * @param noti
     * @return
     */
    boolean sendTimeRecorder(Notification noti);
//
//    boolean sendTimeRecorder(JEVisObject notiObj, Notification noti);

    /**
     * To check, whether the jevis object is of one of the type, which implements interface NotificationDriver, so that the object can used to initialize the driver.
     * @param driverObj
     * @return 
     */
    boolean isConfigurationObject(JEVisObject driverObj);

    /**
     * To get the jevis object, which is used to initialize the driver.
     * @return 
     */
    JEVisObject getJEVisObjectDriver();
    
    /**
     *
     * @param str
     */
    void setNotificationDriver(List<String> str);

}
