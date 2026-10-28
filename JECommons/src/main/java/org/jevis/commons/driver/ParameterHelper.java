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
package org.jevis.commons.driver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisFile;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.classes.JC;
import org.joda.time.DateTime;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ParameterHelper {

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(ParameterHelper.class);
    private final DateTime lastReadout;
    private final DateTime currentTime;

    public ParameterHelper(DateTime lastReadout, DateTime currentTime) {
        this.lastReadout = lastReadout;
        this.currentTime = currentTime;
    }

    public String getNewPath(String path, JEVisObject channelObject) {
        return fillVariables(path, channelObject);
    }

    /**
     * Replaces parameter placeholders (e.g. {CURRENT_TS}) defined in the
     * channel's ParameterConfig in the given text. Used for both the request
     * path and the request body.
     */
    public String fillVariables(String text, JEVisObject channelObject) {
        JEVisFile parameterFile = null;
        if (channelObject != null) {
            try {
                JEVisAttribute parameterConfigAttribute = channelObject.getAttribute(JC.Channel.HTTPChannel.a_ParameterConfig);
                parameterFile = getParameterFile(parameterConfigAttribute.getLatestSample());
            } catch (Exception e) {
                logger.error(e);
            }
        }

        if (parameterFile == null) return text;

        Map<VarFiller.Variable, VarFiller.VarFunction> parameterMap = getParamterMap(parameterFile);
        VarFiller varFiller = new VarFiller(text, parameterMap);

        return varFiller.getFilledURIString();
    }


    private Map<VarFiller.Variable, VarFiller.VarFunction> getParamterMap(JEVisFile jeVisFile) {

        String json = new String(jeVisFile.getBytes(), StandardCharsets.UTF_8);

        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        Type listType = new TypeToken<ArrayList<Parameter>>() {
        }.getType();
        List<Parameter> Parameters = gson.fromJson(json, listType);
        for (Parameter Parameter : Parameters) {
            Parameter.setLastTS(lastReadout);
            Parameter.setCurrentTS(currentTime);
        }

        Map<VarFiller.Variable, VarFiller.VarFunction> map = Parameters.stream().collect(Collectors.toMap(choice -> choice.getVariable(), choice -> choice));

        return map;


    }


    private JEVisFile getParameterFile(JEVisSample jeVisSample) {
        try {
            if (jeVisSample != null) {
                return jeVisSample.getValueAsFile();
            }
        } catch (Exception e) {
            logger.error(e);
        }
        return null;
    }

}
