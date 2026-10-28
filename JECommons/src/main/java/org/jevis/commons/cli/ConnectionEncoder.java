/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.cli;

import org.jevis.api.JEVisOption;
import org.jevis.commons.config.BasicOption;
import org.jevis.commons.config.CommonOptions;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Temporary solution to mask the DataSource authentication.
 *
 * This function can be removed if the JEAPI-WS is implemented.
 *
 * @author fs
 */
public class ConnectionEncoder {

    private static final String seperator = "|";

    public static String encode(String host, String port, String schema, String user, String pw) {
        String toEncode = "" + host + seperator + port + seperator + schema + seperator + user + seperator + pw;
        return Base64.getEncoder().encodeToString(toEncode.getBytes(StandardCharsets.UTF_8));
    }

    public static JEVisOption decode(String value) {
        byte[] decodedValue = Base64.getDecoder().decode(value);
        String asSting = new String(decodedValue, StandardCharsets.UTF_8);
//        String encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));

        String[] segs = asSting.split(Pattern.quote(seperator));
        JEVisOption datasource = new BasicOption();
        datasource.setKey(CommonOptions.DataSource.DataSource.getKey());

        JEVisOption host = new BasicOption();
        host.setKey(CommonOptions.DataSource.HOST.getKey());
        host.setValue(segs[0]);

        JEVisOption port = new BasicOption();
        port.setKey(CommonOptions.DataSource.PORT.getKey());
        port.setValue(segs[1]);

        JEVisOption schema = new BasicOption();
        schema.setKey(CommonOptions.DataSource.SCHEMA.getKey());
        schema.setValue(segs[2]);

        JEVisOption user = new BasicOption();
        user.setKey(CommonOptions.DataSource.USERNAME.getKey());
        user.setValue(segs[3]);

        JEVisOption pass = new BasicOption();
        pass.setKey(CommonOptions.DataSource.PASSWORD.getKey());
        pass.setValue(segs[4]);

        JEVisOption locale = new BasicOption();
        pass.setKey(CommonOptions.DataSource.LOCALE.getKey());
        pass.setValue(segs[5]);

        JEVisOption dsclass = new BasicOption();
        dsclass.setKey(CommonOptions.DataSource.CLASS.getKey());
        dsclass.setValue("org.jevis.api.sql.JEVisDataSourceSQL");

        datasource.addOption(host, true);
        datasource.addOption(port, true);
        datasource.addOption(schema, true);
        datasource.addOption(user, true);
        datasource.addOption(pass, true);
        datasource.addOption(locale, true);
        datasource.addOption(dsclass, true);

//        for (JEVisOption jo : datasource.getOptions()) {
//            logger.info("KEY: " + jo.getKey() + "   Value: " + jo.getValue());
//        }
        return datasource;
    }
}
