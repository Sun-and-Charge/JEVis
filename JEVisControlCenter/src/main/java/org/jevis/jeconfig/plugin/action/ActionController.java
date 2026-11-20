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
package org.jevis.jeconfig.plugin.action;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisObject;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.plugin.action.data.ActionData;
import org.jevis.jeconfig.plugin.action.data.ActionPlanData;
import org.jevis.jeconfig.plugin.action.data.ActionPlanOverviewData;
import org.jevis.jeconfig.plugin.action.ui.*;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ActionController {
    private static final Logger logger = LogManager.getLogger(ActionController.class);
    private final ActionPlugin plugin;
    private final ScrollPane scrollPane = new ScrollPane();
    private final AnchorPane contentPane = new AnchorPane();
    private final TabPane tabPane = new TabPane();
    private final BooleanProperty isOverviewTab = new SimpleBooleanProperty(true);
    private ObservableList<ActionPlanData> actionPlans;
    private ObservableList<ActionPlanData> actionPlansFilters;
    private ObservableList<String> actionPlanNames;


    public ActionController(ActionPlugin plugin) {

        this.plugin = plugin;
        AnchorPane.setBottomAnchor(tabPane, 0.0);
        AnchorPane.setTopAnchor(tabPane, 0.0);
        AnchorPane.setRightAnchor(tabPane, 0.0);
        AnchorPane.setLeftAnchor(tabPane, 0.0);
        contentPane.getChildren().add(tabPane);
    }

    public void loadActionView() {

        actionPlans = FXCollections.observableArrayList();
        actionPlanNames = FXCollections.observableArrayList();
        actionPlansFilters = FXCollections.observableArrayList();

        actionPlans.addListener(new ListChangeListener<ActionPlanData>() {
            @Override
            public void onChanged(Change<? extends ActionPlanData> c) {
                while (c.next()) {
                    //System.out.println("!!!!!! Controller: " + c);
                    if (c.wasAdded()) {
                        c.getAddedSubList().forEach(actionPlan -> {
                            buildTabPane(actionPlan);
                            actionPlanNames.add(actionPlan.getName().get());
                        });

                    }
                }
            }
        });


        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            isOverviewTab.set(getActiveActionPlan() instanceof ActionPlanOverviewData);
        });

    }

    public void reload() {
        actionPlans.forEach(actionPlanData -> {
            try {
                JEConfig.getDataSource().reloadObject(actionPlanData.getObject());
                JEConfig.getDataSource().reloadAttribute(actionPlanData.getObject());

            } catch (Exception ex) {
                logger.error("Error while reloading ActionPlan", ex);
            }
        });

        try {
            JEVisClass actionClass = JEConfig.getDataSource().getJEVisClass("Action");
            List<JEVisObject> actions = JEConfig.getDataSource().getObjects(actionClass, true);
            actions.forEach(jeVisObject -> {
                try {
                    JEConfig.getDataSource().reloadObject(jeVisObject);
                    JEConfig.getDataSource().reloadAttribute(jeVisObject);
                } catch (Exception ex) {

                }
            });

        } catch (Exception exception) {

        }

        actionPlans.clear();
        tabPane.getTabs().clear();
        loadActionView();
        loadActionPlans();

    }


    private void buildTabPane(ActionPlanData plan) {
        ActionTab tab = new ActionTab(this, plan);
        tab.setClosable(false);
        tabPane.getTabs().add(tab);
    }

    public TabPane getTabPane() {
        return tabPane;
    }

    public ObservableList<ActionPlanData> getActionPlans() {
        return actionPlans;
    }

    public void deletePlan() {
        /* the overview tab is no plan of its own: nothing to delete or configure (toolbar buttons are disabled there) */
        if (getActiveActionPlan() == null || getActiveActionPlan() instanceof ActionPlanOverviewData) return;
        ActionTab tab = getActiveTab();

        /* J25.5: question naming the entry, consequence, red action button (docs/DESIGN-CC.md) */
        String entryName = getActiveActionPlan().getName().get();
        if (org.jevis.jeconfig.application.ui.Confirm.ask(
                String.format(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.action.plan.confirm.question", "Aktionsplan „%s“ löschen?", "Delete action plan “%s”?"), entryName),
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.action.plan.confirm.consequence", "Der Plan kommt mit allen Maßnahmen in den Papierkorb und lässt sich von dort wiederherstellen.", "The plan and all its actions move to the recycle bin and can be restored from there."),
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.delete", "Löschen", "Delete"), true)) {
            try {
                getActiveActionPlan().delete();
                actionPlans.remove(tab.getActionPlan());
                tabPane.getTabs().remove(tab);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }


    }

    public void exportPDF() {
        ObservableList<ActionPlanData> toExport = FXCollections.observableArrayList();
        getTabPane().getTabs().forEach(tab -> {
            toExport.add(((ActionTab) tab).getActionPlan());
        });
        ExportDialog exportDialog = new ExportDialog(toExport);

        /* J25.5: the button names the action ("Exportieren …", a file dialog follows) */
        ButtonType buttonTypeOne = new ButtonType(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.graph.export.action", "Exportieren …", "Export …"), ButtonBar.ButtonData.OK_DONE);
        exportDialog.addPrimary(buttonTypeOne);
        exportDialog.addCancel();

        Optional<ButtonType> optional = exportDialog.showAndWait();
        if (optional.isPresent() && optional.get() == buttonTypeOne) {
            ExcelExporter excelExporter = new ExcelExporter(this, exportDialog.getSelection());
        } else {

        }


    }

    public void createNewPlan() {
        NewActionDialog newActionDialog = new NewActionDialog();
        try {
            NewActionDialog.Response response = newActionDialog.show(JEConfig.getStage(), plugin.getDataSource());
            if (response == NewActionDialog.Response.YES) {

                JEVisClass actionPlanClass = plugin.getDataSource().getJEVisClass("Action Plan v2");
                JEVisObject parentDir = newActionDialog.getParent();

                JEVisObject newObject = parentDir.buildObject(newActionDialog.getCreateName(), actionPlanClass);
                newObject.commit();
                ActionPlanData actionPlan = new ActionPlanData(newObject);

                actionPlan.setDefaultValues(Locale.GERMANY);//For now only German is Supportet
                actionPlan.commit();
                actionPlans.add(actionPlan);
                tabPane.getSelectionModel().selectLast();

            }

        } catch (Exception ex) {
            logger.error(ex);
        }


    }


    public void deleteAction() {
        ActionTab tab = getActiveTab();

        /* J25.5: question naming the entry, consequence, red action button (docs/DESIGN-CC.md) */
        String entryName = getSelectedData().noProperty().get() + " " + getSelectedData().titleProperty().get();
        if (org.jevis.jeconfig.application.ui.Confirm.ask(
                String.format(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.action.action.confirm.question", "Maßnahme „%s“ löschen?", "Delete action “%s”?"), entryName),
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.action.action.confirm.consequence", "Die Maßnahme wird aus dem Aktionsplan entfernt.", "The action is removed from the action plan."),
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.delete", "Löschen", "Delete"), true)) {
            try {
                getSelectedData().delete();
                tab.getActionTable().filter();
                //tab.getActionPlan().removeAction(tab.getActionTable().getSelectionModel().getSelectedItem());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }


    public void createNewAction() {
        ActionTab tab = getActiveTab();
        try {
            JEVisClass actionDirClass = getActiveActionPlan().getObject().getDataSource().getJEVisClass("Action Directory");
            JEVisClass actionClass = getActiveActionPlan().getObject().getDataSource().getJEVisClass("Action");
            JEVisObject actionDirObj = null;
            if (getActiveActionPlan().getObject().getChildren(actionDirClass, false).isEmpty()) {
                actionDirObj = getActiveActionPlan().getObject().buildObject(actionDirClass.getName(), actionDirClass);
                actionDirObj.commit();
            } else {
                actionDirObj = getActiveActionPlan().getObject().getChildren(actionDirClass, false).get(0);
            }

            int nextNr = getActiveActionPlan().getNextActionNr();
            JEVisObject actionObject = actionDirObj.buildObject(nextNr + "", actionClass);
            actionObject.commit();
            ActionData newAction = new ActionData(tab.getActionPlan(), actionObject);
            newAction.noProperty().set(nextNr);
            String userName = actionDirObj.getDataSource().getCurrentUser().getFirstName() + " " + actionDirObj.getDataSource().getCurrentUser().getLastName();
            if (userName.trim().isEmpty()) userName = actionDirObj.getDataSource().getCurrentUser().getFirstName();
            newAction.fromUser.set(userName);


            newAction.commit();
            tab.getActionPlan().addAction(newAction);
            tab.getActionTable().filter();
            tab.getActionTable().getSelectionModel().select(newAction);

            openDataForm();//tab.getActionTable().getSelectionModel().getSelectedItem()
        } catch (Exception ex) {
            logger.error(ex);
        }
    }

    public void openPlanSettings() {
        /* the overview tab is no plan of its own: nothing to delete or configure (toolbar buttons are disabled there) */
        if (getActiveActionPlan() == null || getActiveActionPlan() instanceof ActionPlanOverviewData) return;
        ActionPlanForm actionPlanForm = new ActionPlanForm(getActiveTab().getActionPlan());
        Optional<ButtonType> result = actionPlanForm.showAndWait();
        if (result.isPresent() && result.get().getButtonData() == ButtonBar.ButtonData.OTHER) {
            actionPlanForm.commitRenames();
            getActiveTab().getActionPlan().commit();
        }

    }

    public void loadActionPlans() {
        try {
            //System.out.println("loadActionPlans.start()");
            JEVisClass actionPlanClass = plugin.getDataSource().getJEVisClass("Action Plan v2");
            List<JEVisObject> planObjs = plugin.getDataSource().getObjects(actionPlanClass, true);

            planObjs.forEach(jeVisObject -> {
                //System.out.println("loadActionPlans: " + jeVisObject.getID());
                ActionPlanData plan = new ActionPlanData(jeVisObject);
                plan.loadActionList();
                actionPlans.add(plan);
            });
            if (planObjs.size() > 1) {
                ActionPlanOverviewData overviewData = new ActionPlanOverviewData(this);
                ActionTab overviewTab = new ActionTab(this, overviewData);
                tabPane.getTabs().add(0, overviewTab);
                overviewData.updateData();
            }


            Platform.runLater(() -> tabPane.getSelectionModel().selectFirst());

        } catch (Exception e) {
            e.printStackTrace();
        }
        //System.out.println("Done loading actionplans");
    }

    public Node getContent() {
        return contentPane;
    }

    public ActionTab getActiveTab() {
        ActionTab tab = (ActionTab) tabPane.getSelectionModel().getSelectedItem();
        return tab;
    }

    public ActionPlanData getActiveActionPlan() {
        if (getActiveTab() == null) {
            return null;
        }
        return getActiveTab().getActionPlan();
    }

    public ActionData getSelectedData() {
        ActionTab tab = getActiveTab();
        if (getActiveTab().getActionTable().getSelectionModel().getSelectedItem() != null) {
            return getActiveTab().getActionTable().getSelectionModel().getSelectedItem();
        } else {
            if (tab.getActionTable().getItems().isEmpty()) {
                {
                    return null;
                }
            }

            return tab.getActionTable().getItems().get(0);
        }
    }

    public void openDataForm() {
        logger.debug("openDataForm()");
        ActionData data = getSelectedData();
        if (data == null) return;
        ActionForm actionForm = new ActionForm(getActiveActionPlan(), data);
        TabPane tabs = actionForm.getTabPane();
        tabs.setPrefWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);

        /* J25.5: the action opens in the side panel (docs/DESIGN-CC.md: editors with more than five fields) */
        org.jevis.jeconfig.application.ui.SidePanel panel = org.jevis.jeconfig.application.ui.SidePanel.get();
        final boolean[] saved = {false};
        boolean shown = panel.show(data.getNoText() + (data.titleProperty().get() == null || data.titleProperty().get().isEmpty() ? "" : " – " + data.titleProperty().get()),
                I18n.getInstance().getString("actionform.editor.title"), tabs, () -> {
                    data.setNew(false);
                    data.commit();
                    getActiveTab().updateStatistics();
                    saved[0] = true;
                    org.jevis.jeconfig.application.ui.Notify.success(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.action.form.saved", "Maßnahme gespeichert", "Action saved"));
                    return true;
                });
        if (!shown) return;
        panel.setPanelWidth(900);
        panel.setDirtyCheck(data::hasChanged);
        panel.setOnClosed(() -> {
            if (saved[0] || data.isNew()) return;
            /* discarded: back to the stored state */
            try {
                int index = getActiveTab().getActionTable().getSelectionModel().getSelectedIndex();
                getActiveActionPlan().reloadAction(data);
                Platform.runLater(() -> getActiveTab().getActionTable().getSelectionModel().select(index));
            } catch (Exception ex) {
                logger.error(ex, ex);
            }
        });
    }

    public boolean isIsOverviewTab() {
        return isOverviewTab.get();
    }

    public BooleanProperty isOverviewTabProperty() {
        return isOverviewTab;
    }
}
