/**
 * Copyright (C) 2015 Envidatec GmbH <info@envidatec.com>
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.driver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates the data source driver for a data source object. The drivers are registered by the service with
 * {@link #setDataSourceClasses(Map)}, keyed by the JEVis class name of the data source.
 *
 * @author Broder
 */
public class DataSourceFactory {
    private static final Logger logger = LogManager.getLogger(DataSourceFactory.class);

    private static Map<String, Class> _dataSourceClasses = new HashMap<>();

    public static void setDataSourceClasses(Map<String, Class> _dataSourceClasses) {
        DataSourceFactory._dataSourceClasses = _dataSourceClasses;
    }

    public static DataSource getDataSource(JEVisObject dataSourceJEVis) {
        logger.debug("getDataSource: {} from {}", dataSourceJEVis, _dataSourceClasses);
        DataSource dataSource = null;
        try {
            Class dataSourceClass = DriverLookup.find(_dataSourceClasses, dataSourceJEVis.getJEVisClass());
            dataSource = (DataSource) dataSourceClass.newInstance();
            logger.debug("done init dataSource");
        } catch (JEVisException | InstantiationException | NullPointerException | IllegalAccessException ex) {
            logger.fatal("Error creating DataSource", ex);
        }
        return dataSource;
    }

    public static boolean containDataSource(JEVisObject dataSourceJEVis) {
        try {
            return DriverLookup.find(_dataSourceClasses, dataSourceJEVis.getJEVisClass()) != null;
        } catch (JEVisException ex) {
            logger.fatal(ex);
            return false;
        }
    }

}
