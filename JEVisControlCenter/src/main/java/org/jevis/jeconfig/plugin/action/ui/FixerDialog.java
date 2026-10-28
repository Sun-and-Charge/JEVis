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
package org.jevis.jeconfig.plugin.action.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.jeconfig.plugin.action.data.ActionPlanData;

/**
 * Use case specific data fixer. Can be deleted in the future.
 */
public class FixerDialog {
    private static final Logger logger = LogManager.getLogger(FixerDialog.class);


    /*
    final ContextMenu contextMenu = new ContextMenu();
    MenuItem item1 = new MenuItem("Fix Title");
    MenuItem item2 = new MenuItem("Fix NPV");
    MenuItem item3 = new MenuItem("Commit all");

        item1.setOnAction(actionEvent -> {
        FixerDialog fixer = new FixerDialog();
        fixer.fixTitel(plan);
    });

        item2.setOnAction(actionEvent -> {
        FixerDialog fixer = new FixerDialog();
        fixer.fixNPV(plan);
    });

        item3.setOnAction(actionEvent -> {
        FixerDialog fixer = new FixerDialog();
        fixer.commitAll(plan);
    });

    public void commitAll(ActionPlanData planData) {
        planData.getActionData().forEach(actionData -> {
            try {
                System.out.println("Commit: " + actionData.nr.get());
                actionData.commit();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        });

    }
     if (JEConfig.getExpert()) {
            contextMenu.getItems().addAll(item1, item2, item3);
            this.setContextMenu(contextMenu);
        }
     */

    public void fixTitel(ActionPlanData planData) {

        planData.getActionData().forEach(actionData -> {
            try {
                String oldTitle = actionData.title.get();
                String oldMaßnahme = actionData.noteEnergieflussProperty().get();
                String oldDescription = actionData.descriptionProperty().get();//Prozess

                logger.debug("Nr: " + actionData.no.get() + " oldTitle: " + oldTitle + "  oldMaßnahme: " + oldMaßnahme);
                actionData.descriptionProperty().set(oldTitle);
                actionData.title.set(oldMaßnahme);
                //actionData.commit();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });


    }

    public void fixNPV(ActionPlanData planData) {
        planData.getActionData().forEach(actionData -> {
            int amountYear = actionData.npv.get().amoutYear.get();
            int overYears = actionData.npv.get().overXYear.get();

            actionData.npv.get().amoutYear.set(10);
            actionData.npv.get().overXYear.set(3);
            //actionData.commit();

        });
    }

}
