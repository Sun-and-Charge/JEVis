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
package org.jevis.jeconfig.plugin.nonconformities;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.classes.JC;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.plugin.nonconformities.data.NonconformityData;
import org.jevis.jeconfig.plugin.nonconformities.data.NonconformityPlan;
import org.jevis.jeconfig.plugin.nonconformities.data.NonconformtiesOverviewData;
import org.jevis.jeconfig.plugin.nonconformities.ui.*;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class NonconformitiesController {
    private static final Logger logger = LogManager.getLogger(NonconformitiesController.class);

    private final NonconformitiesPlugin plugin;

    private final ScrollPane scrollPane = new ScrollPane();
    private final AnchorPane contentPane = new AnchorPane();
    private ObservableList<NonconformityPlan> nonconformityPlanList;
    private TabPane tabPane = new TabPane();
    private BooleanProperty isOverviewTab = new SimpleBooleanProperty(true);

    private BooleanProperty updateTrigger = new SimpleBooleanProperty(false);

    public NonconformitiesController(NonconformitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadActionView() {
        nonconformityPlanList = FXCollections.observableArrayList();
        nonconformityPlanList.addListener(new ListChangeListener<NonconformityPlan>() {
            @Override
            public void onChanged(Change<? extends NonconformityPlan> c) {
                while (c.next()) {
                    if (c.wasAdded()) {
                        c.getAddedSubList().forEach(actionPlan -> {
                            buildTabPane(actionPlan);
                        });

                    }
                }


            }
        });
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            isOverviewTab.set(getActiveNonconformityPlan() instanceof NonconformtiesOverviewData);
        });


        AnchorPane.setBottomAnchor(tabPane, 0.0);
        AnchorPane.setTopAnchor(tabPane, 0.0);
        AnchorPane.setRightAnchor(tabPane, 0.0);
        AnchorPane.setLeftAnchor(tabPane, 0.0);
        contentPane.getChildren().add(tabPane);


    }


    private void buildTabPane(NonconformityPlan nonconformityPlanPlan) {

        NonconformityPlanTable nonconformityPlanTable = new NonconformityPlanTable(nonconformityPlanPlan, nonconformityPlanPlan.getActionData(), updateTrigger);

        //actionTable.enableSumRow(true);
        NonconformityPlanTab tab = new NonconformityPlanTab(nonconformityPlanPlan, this, updateTrigger);
        tab.setClosable(false);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            logger.info("new tab selected: {}", newValue);

            if (newValue instanceof NonconformityPlanTab) {
                NonconformityPlan nonconformityPlan = ((NonconformityPlanTab) newValue).getNonconformityPlan();
                //actionPlan.loadActionList();
            }

        });

        nonconformityPlanTable.sort();

        //actionTable.setItems(createTestData());

    }

    public void deletePlan() {
        /* the overview tab is no plan of its own: nothing to delete or configure (toolbar buttons are disabled there) */
        if (getActiveNonconformityPlan() == null || getActiveNonconformityPlan() instanceof org.jevis.jeconfig.plugin.nonconformities.data.NonconformtiesOverviewData) return;
        NonconformityPlanTab tab = getActiveTab();

        /* J25.5: question naming the entry, consequence, red action button (docs/DESIGN-CC.md) */
        String entryName = getActiveNonconformityPlan().getName().get();
        if (org.jevis.jeconfig.application.ui.Confirm.ask(
                String.format(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.nonconformities.plan.confirm.question", "Abweichungsplan „%s“ löschen?", "Delete nonconformity plan “%s”?"), entryName),
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.nonconformities.plan.confirm.consequence", "Der Plan kommt mit allen Abweichungen in den Papierkorb und lässt sich von dort wiederherstellen.", "The plan and all its nonconformities move to the recycle bin and can be restored from there."),
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.delete", "Löschen", "Delete"), true)) {
            try {
                getActiveNonconformityPlan().delete();
                nonconformityPlanList.remove(tab.getNonconformityPlan());
                tabPane.getTabs().remove(tab);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }


    }

    public void createNewNonconformityPlan() {
        NewNonconformitiesDialog newNonconformitiesDialog = new NewNonconformitiesDialog();
        try {
            NewNonconformitiesDialog.Response response = newNonconformitiesDialog.show(JEConfig.getStage(), plugin.getDataSource());
            if (response == NewNonconformitiesDialog.Response.YES) {

                JEVisClass nonconformityPlanClass = plugin.getDataSource().getJEVisClass(JC.NonconformitiesPlan.name);
                JEVisObject parentDir = newNonconformitiesDialog.getParent();

                JEVisObject newObject = parentDir.buildObject(newNonconformitiesDialog.getCreateName(), nonconformityPlanClass);
                newObject.commit();
                NonconformityPlan nonconformityPlan = new NonconformityPlan(newObject);
                this.nonconformityPlanList.add(nonconformityPlan);
                tabPane.getSelectionModel().selectLast();

                DateTime now = new DateTime();
                JEVisSample mediumAtt = newObject.getAttribute("Custom Medium").buildSample(now, "Strom;Gas;Wasser");
                mediumAtt.commit();
            }

        } catch (Exception ex) {
            logger.error(ex);
        }


    }


    public void deleteNonconformity() {
        NonconformityPlanTab tab = getActiveTab();

        /* J25.5: question naming the entry, consequence, red action button (docs/DESIGN-CC.md) */
        String entryName = getSelectedData().nrProperty().get() + " " + getSelectedData().titleProperty().get();
        if (org.jevis.jeconfig.application.ui.Confirm.ask(
                String.format(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.nonconformities.item.confirm.question", "Abweichung „%s“ löschen?", "Delete nonconformity “%s”?"), entryName),
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.nonconformities.item.confirm.consequence", "Die Abweichung wird aus dem Abweichungsplan entfernt.", "The nonconformity is removed from the plan."),
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.delete", "Löschen", "Delete"), true)) {
            try {
                getSelectedData().setDeleted(true);
                getSelectedData().commit();
                tab.getNonconformityPlan().removeNonconformity(tab.getActionTable().getSelectionModel().getSelectedItem());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }


    public void createNonconformity() {
        NonconformityPlanTab tab = getActiveTab();
        try {
            JEVisClass nonconformityPlanDirClass = getActiveNonconformityPlan().getObject().getDataSource().getJEVisClass(JC.NonconformitiesPlan.NonconformityPlanDirectory.Nonconformity.NonconformityDirectory.name);
            JEVisClass nonconformityClass = getActiveNonconformityPlan().getObject().getDataSource().getJEVisClass(JC.NonconformitiesPlan.NonconformityPlanDirectory.Nonconformity.name);
            JEVisObject nonconformityPlanDirObj = null;
            if (getActiveNonconformityPlan().getObject().getChildren(nonconformityPlanDirClass, false).isEmpty()) {
                nonconformityPlanDirObj = getActiveNonconformityPlan().getObject().buildObject(nonconformityPlanDirClass.getName(), nonconformityPlanDirClass);

                nonconformityPlanDirObj.commit();
            } else {
                nonconformityPlanDirObj = getActiveNonconformityPlan().getObject().getChildren(nonconformityPlanDirClass, false).get(0);
            }

            int nextNonconformityNr = getActiveNonconformityPlan().getNextNonconformityNr();

            JEVisObject nonconformityObject = nonconformityPlanDirObj.buildObject(String.valueOf(nextNonconformityNr), nonconformityClass);
            nonconformityObject.commit();
            NonconformityData newNonconformityData = new NonconformityData(nonconformityObject, tab.getNonconformityPlan());
            newNonconformityData.commit();
            newNonconformityData.nrProperty().set(nextNonconformityNr);
            newNonconformityData.setCreator(getActiveNonconformityPlan().getObject().getDataSource().getCurrentUser().getFirstName() + " " + getActiveNonconformityPlan().getObject().getDataSource().getCurrentUser().getLastName());
            tab.getNonconformityPlan().addAction(newNonconformityData);

            tab.getActionTable().getSelectionModel().select(newNonconformityData);
            openDataForm(true);//tab.getActionTable().getSelectionModel().getSelectedItem()
        } catch (Exception ex) {
            logger.error(ex);
        }
    }

    public void openPlanSettings() {
        /* the overview tab is no plan of its own: nothing to delete or configure (toolbar buttons are disabled there) */
        if (getActiveNonconformityPlan() == null || getActiveNonconformityPlan() instanceof org.jevis.jeconfig.plugin.nonconformities.data.NonconformtiesOverviewData) return;
        NonconfomityPlanForm nonconfomityPlanForm = new NonconfomityPlanForm(getActiveTab().getNonconformityPlan());
        Optional<ButtonType> result = nonconfomityPlanForm.showAndWait();
        if (result.isPresent() && result.get().getButtonData() == ButtonBar.ButtonData.OTHER) {
            getActiveTab().getNonconformityPlan().commit();
        }

    }

    public void loadNonconformityPlans() {
        try {
            JEVisClass actionPlanClass = plugin.getDataSource().getJEVisClass(JC.NonconformitiesPlan.name);
            List<JEVisObject> planObjs = plugin.getDataSource().getObjects(actionPlanClass, true);

            NonconformtiesOverviewData overviewData = new NonconformtiesOverviewData(this);
            NonconformityPlanTab overviewTab = new NonconformityPlanTab(overviewData,this, updateTrigger);
            overviewTab.setClosable(false);
            tabPane.getTabs().add(0, overviewTab);


            AtomicBoolean isFirstPlan = new AtomicBoolean(true);

            planObjs.forEach(jeVisObject -> {
                NonconformityPlan plan = new NonconformityPlan(jeVisObject);
                nonconformityPlanList.add(plan);
                if (isFirstPlan.get()) plan.loadNonconformityList();
                isFirstPlan.set(false);
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Node getContent() {
        return contentPane;
    }

    public NonconformityPlanTab getActiveTab() {
        NonconformityPlanTab tab = (NonconformityPlanTab) tabPane.getSelectionModel().getSelectedItem();
        return tab;
    }

    public NonconformityPlan getActiveNonconformityPlan() {
        return getActiveTab().getNonconformityPlan();
    }

    public NonconformityData getSelectedData() {
        NonconformityPlanTab tab = getActiveTab();
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

    public void openDataForm(boolean isNew) {
        NonconformityData data = getSelectedData();
        if (data == null) return;
        NonconformityForm nonconformityForm = new NonconformityForm(getActiveNonconformityPlan());
        nonconformityForm.setData(data);
        nonconformityForm.setNew(isNew);
        javafx.scene.control.TabPane tabs = nonconformityForm.tabPane;
        tabs.setPrefWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);

        /* J25.5: the nonconformity opens in the side panel (docs/DESIGN-CC.md: editors with more than five fields) */
        org.jevis.jeconfig.application.ui.SidePanel panel = org.jevis.jeconfig.application.ui.SidePanel.get();
        final boolean[] saved = {false};
        boolean shown = panel.show(I18n.getInstance().getString("plugin.nonconformities.nonconformity.dialog.title"),
                I18n.getInstance().getString("plugin.nonconformities.nonconformity.dialog.header"), tabs, () -> {
                    String errorText = data.checkForRequirements();
                    if (!errorText.equals(NonconformityData.REQUIREMENTS_MET)) {
                        nonconformityForm.showNotification(errorText, Icon.Warning);
                        return false;
                    }
                    data.commit();
                    updateTrigger.set(!updateTrigger.get());
                    saved[0] = true;
                    org.jevis.jeconfig.application.ui.Notify.success(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.nonconformities.form.saved", "Abweichung gespeichert", "Nonconformity saved"));
                    return true;
                });
        if (!shown) return;
        panel.setPanelWidth(900);
        panel.setOnClosed(() -> {
            if (saved[0]) return;
            if (isNew) {
                try {
                    data.getObject().delete();
                    getActiveTab().getNonconformityPlan().getNonconformityList().remove(data);
                } catch (Exception e) {
                    logger.error(e);
                }
            } else {
                reload(data);
            }
        });
    }

    @NotNull
    private EventHandler getCloseRequest(NonconformityData data, NonconformityForm nonconformityForm) {
        return dialogEvent -> {
            String errorText = data.checkForRequirements();
            logger.debug(errorText);
            if (!errorText.equals(NonconformityData.REQUIREMENTS_MET)) {
                nonconformityForm.showNotification(errorText,Icon.Warning);
                dialogEvent.consume();

            }  else {
                data.commit();
                updateTrigger.set(!updateTrigger.get());
            }
        };
    }

    private void reload(NonconformityData nonconformityData) {
        try {
            nonconformityData = getActiveNonconformityPlan().loadNonconformties(nonconformityData.getObject());
        } catch (Exception e) {
           logger.error(e);
        }
    }


    public boolean isIsOverviewTab() {
        return isOverviewTab.get();
    }

    public BooleanProperty isOverviewTabProperty() {
        return isOverviewTab;
    }

    public void setIsOverviewTab(boolean isOverviewTab) {
        this.isOverviewTab.set(isOverviewTab);
    }

    public ObservableList<NonconformityPlan> getNonconformityPlanList() {
        return nonconformityPlanList;
    }

    public void setNonconformityPlanList(ObservableList<NonconformityPlan> nonconformityPlanList) {
        this.nonconformityPlanList = nonconformityPlanList;
    }
}
