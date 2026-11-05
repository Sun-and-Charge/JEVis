/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
package org.jevis.jeconfig.tool.template;

import com.jfoenix.controls.JFXCheckBox;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.classes.JC;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.permission.BuildingRightsTemplate;
import org.jevis.commons.permission.RightsModel;
import org.jevis.commons.permission.RightsPlan;
import org.jevis.commons.permission.RightsPlanExecutor;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.Boxes.TimeZoneBox;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.HashMap;
import java.util.Map;

/**
 * Template for a new building. The directories, groups, roles and rights of the building come from
 * {@link BuildingRightsTemplate}, which is also used to bring an existing building to this structure
 * ("Rechte nach Vorlage einrichten").
 */
public class BuildingObject extends Template {
    public static final int THREAD_WAIT = 500;
    private static final Logger logger = LogManager.getLogger(BuildingObject.class);
    private final SimpleBooleanProperty withNoEntryPointGroups = new SimpleBooleanProperty(this, "withNoEntryPointGroups", false);

    private final SimpleObjectProperty<DateTimeZone> timeZone = new SimpleObjectProperty<>(this, "timeZone", DateTimeZone.UTC);

    @Override
    public String getName() {
        return I18n.getInstance().getString("alarms.table.captions.building");
    }

    @Override
    public boolean create(JEVisClass jclass, JEVisObject parent, String name) throws JEVisException, InterruptedException {
        JEVisDataSource ds = parent.getDataSource();
        JEVisClass buildingClass = ds.getJEVisClass("Building");

        JEVisObject buildingObject = parent.buildObject("Building", buildingClass);
        buildingObject.setLocalName(I18n.getInstance().getLocale().getLanguage(), name);
        buildingObject.commit();

        JEVisAttribute timezoneAttribute = buildingObject.getAttribute(JC.MonitoredObject.Building.a_Timezone);
        JEVisSample timeZoneSample = timezoneAttribute.buildSample(new DateTime(), this.timeZone.get().getID());
        timeZoneSample.commit();
        Thread.sleep(THREAD_WAIT);

        RightsModel model = RightsModel.fromDataSource(ds, false);
        if (model.get(buildingObject.getID()) == null) {
            model.addObject(buildingObject.getID(), buildingObject.getName(), "Building");
        }

        BuildingRightsTemplate.Options options = new BuildingRightsTemplate.Options();
        options.withNoEntryPointGroups = withNoEntryPointGroups.get();
        options.createServiceUser = true;
        options.createMissingDirectories = true;
        RightsPlan plan = new BuildingRightsTemplate(model, buildingObject.getID(), name).plan(options);
        plan.getWarnings().forEach(warning -> logger.warn("Building template: {}", warning));

        RightsPlanExecutor.Result result = new RightsPlanExecutor(ds).setObjectDelayMs(THREAD_WAIT).execute(plan, null);
        if (result.getFailed() > 0) {
            logger.error("Building template: {} of {} changes failed", result.getFailed(), plan.getActions().size());
        }
        return result.getFailed() == 0;
    }

    @Override
    public boolean supportsClass(JEVisClass jClass) throws JEVisException {
        return jClass.getName().equals("Building");
    }

    @Override
    public Map<String, Node> getOptions() {
        Map<String, Node> optionMap = new HashMap<>();

        String withNoEntryPointGroupsName = "Create No Entry Points Groups";
        JFXCheckBox withNoEntryPointGroupsBox = new JFXCheckBox(withNoEntryPointGroupsName);
        withNoEntryPointGroupsBox.selectedProperty().bindBidirectional(withNoEntryPointGroups);

        String timeZone = "Timezone";
        TimeZoneBox timeZoneBox = new TimeZoneBox();
        timeZoneBox.valueProperty().bindBidirectional(this.timeZone);

        optionMap.put(withNoEntryPointGroupsName, withNoEntryPointGroupsBox);
        optionMap.put(timeZone, timeZoneBox);

        return optionMap;
    }
}
