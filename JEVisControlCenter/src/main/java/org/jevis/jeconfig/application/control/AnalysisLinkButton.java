/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.application.control;

import com.jfoenix.controls.JFXButton;
import javafx.scene.Node;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.plugin.AnalysisRequest;
import org.jevis.jeconfig.plugin.charts.ChartPlugin;
import org.joda.time.DateTime;

public class AnalysisLinkButton extends JFXButton {

    private static final Logger logger = LogManager.getLogger(AnalysisLinkButton.class);
    Node icon;
    private AnalysisRequest analysisRequest;

    public AnalysisLinkButton() {
        super();
        initStyle();
    }

    public AnalysisLinkButton(JEVisAttribute attribute) {
        super("", JEConfig.getImage("1415314386_Graph.png", 20, 20));
        initStyle();
        init(attribute);
    }

    private void initStyle() {
        setStyle("-fx-background-color: transparent;");
        setGraphic(JEConfig.getSVGImage(Icon.GRAPH, 20, 20));
    }

    public void init(JEVisAttribute attribute) {
        if (attribute != null) {
            try {
                DateTime timestampFromLastSample = attribute.getTimestampOfLastSample();

                DateTime startDateFromSampleRate = org.jevis.commons.utils.CommonMethods.getStartDateFromSampleRate(attribute);

                analysisRequest = new AnalysisRequest(attribute.getObject(),
                        AggregationPeriod.NONE,
                        ManipulationMode.NONE,
                        startDateFromSampleRate, timestampFromLastSample);
                analysisRequest.setAttribute(attribute);

                setOnAction(event -> JEConfig.openObjectInPlugin(ChartPlugin.PLUGIN_NAME, analysisRequest));

            } catch (Exception e) {
                logger.error("Could not build analysis link button: ", e);
                setDisable(true);
            }
        } else {
            setDisable(true);
        }
    }

    public AnalysisRequest getAnalysisRequest() {
        return analysisRequest;
    }
}
