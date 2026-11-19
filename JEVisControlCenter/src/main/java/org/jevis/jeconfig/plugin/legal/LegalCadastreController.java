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
package org.jevis.jeconfig.plugin.legal;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisObject;
import org.jevis.commons.classes.JC;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.plugin.legal.data.IndexOfLegalProvisions;
import org.jevis.jeconfig.plugin.legal.data.ObligationData;
import org.jevis.jeconfig.plugin.legal.ui.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class LegalCadastreController {
    private static final Logger logger = LogManager.getLogger(LegalCadastreController.class);

    private final LegalCatasdrePlugin plugin;

    private final ScrollPane scrollPane = new ScrollPane();
    private final AnchorPane contentPane = new AnchorPane();
    private ObservableList<IndexOfLegalProvisions> indexOfLegalProvisions;
    private final TabPane tabPane = new TabPane();

    private final BooleanProperty updateTrigger = new SimpleBooleanProperty(false);
//    private BooleanProperty isOverviewTab = new SimpleBooleanProperty(true);

    private final BooleanProperty inAlarm = new SimpleBooleanProperty();

    public LegalCadastreController(LegalCatasdrePlugin plugin) {
        this.plugin = plugin;
    }

    public void loadIndexOfLegalProvisions() {
        indexOfLegalProvisions = FXCollections.observableArrayList();
        indexOfLegalProvisions.addListener(new ListChangeListener<IndexOfLegalProvisions>() {
            @Override
            public void onChanged(Change<? extends IndexOfLegalProvisions> c) {
                while (c.next()) {
                    if (c.wasAdded()) {
                        c.getAddedSubList().forEach(indexOfLegalProvisions -> {
                            buildTabPane(indexOfLegalProvisions);
                        });

                    }
                }


            }
        });

        AnchorPane.setBottomAnchor(tabPane, 0.0);
        AnchorPane.setTopAnchor(tabPane, 0.0);
        AnchorPane.setRightAnchor(tabPane, 0.0);
        AnchorPane.setLeftAnchor(tabPane, 0.0);
        contentPane.getChildren().add(tabPane);


    }


    private void buildTabPane(IndexOfLegalProvisions indexOfLegalProvisions) {

        IndexOfLegalProvisionsTable indexOfLegalProvisionsTable = new IndexOfLegalProvisionsTable(indexOfLegalProvisions, indexOfLegalProvisions.getLegislationDataList(), updateTrigger);

        //actionTable.enableSumRow(true);
        LegalCadastreTab tab = new LegalCadastreTab(indexOfLegalProvisions, this, updateTrigger);
        tab.setClosable(false);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            logger.info("new tab selected: {}", newValue);

            if (newValue instanceof LegalCadastreTab) {
                IndexOfLegalProvisions legalPlan = ((LegalCadastreTab) newValue).getLegalCadastre();
                legalPlan.loadLegalList();
            }

        });

        indexOfLegalProvisionsTable.sort();

        //actionTable.setItems(createTestData());

    }

    public void deletePlan() {
        LegalCadastreTab tab = getActiveTab();

        /* J25.5: question naming the entry, consequence, red action button (docs/DESIGN-CC.md) */
        String entryName = getActivePlan().getName().get();
        if (org.jevis.jeconfig.application.ui.Confirm.ask(
                String.format(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.indexoflegalprovisions.plan.confirm.question", "Rechtskataster „%s“ löschen?", "Delete legal register “%s”?"), entryName),
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.indexoflegalprovisions.plan.confirm.consequence", "Das Rechtskataster kommt mit allen Rechtsvorschriften in den Papierkorb und lässt sich von dort wiederherstellen.", "The register and all its provisions move to the recycle bin and can be restored from there."),
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.delete", "Löschen", "Delete"), true)) {
            try {
                getActivePlan().delete();
                indexOfLegalProvisions.remove(tab.getLegalCadastre());
                tabPane.getTabs().remove(tab);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }


    }

    public void createNewPlan() {
        NewlegalCadastreDialog newlegalCadastreDialog = new NewlegalCadastreDialog();
        try {
            NewlegalCadastreDialog.Response response = newlegalCadastreDialog.show(JEConfig.getStage(), plugin.getDataSource());
            if (response == NewlegalCadastreDialog.Response.YES) {

                JEVisClass actionPlanClass = plugin.getDataSource().getJEVisClass(JC.IndexofLegalProvisions.name);
                JEVisObject parentDir = newlegalCadastreDialog.getParent();

                JEVisObject newObject = parentDir.buildObject(newlegalCadastreDialog.getCreateName(), actionPlanClass);
                newObject.commit();
                IndexOfLegalProvisions legalPlan = new IndexOfLegalProvisions(newObject);
                this.indexOfLegalProvisions.add(legalPlan);
                tabPane.getSelectionModel().selectLast();

//                DateTime now = new DateTime();
////                JEVisSample mediumAtt = newObject.getAttribute("Custom Medium").buildSample(now, "Strom;Gas;Wasser");
//                mediumAtt.commit();
            }

        } catch (Exception ex) {
            logger.error(ex);
        }


    }


    public void deleteItem() {
        LegalCadastreTab tab = getActiveTab();

        /* J25.5: question naming the entry, consequence, red action button (docs/DESIGN-CC.md) */
        String entryName = getSelectedData().nrProperty().get() + " " + getSelectedData().titleProperty().get();
        if (org.jevis.jeconfig.application.ui.Confirm.ask(
                String.format(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.indexoflegalprovisions.obligation.confirm.question", "Rechtsvorschrift „%s“ entfernen?", "Remove provision “%s”?"), entryName),
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.indexoflegalprovisions.obligation.confirm.consequence", "Die Rechtsvorschrift wird aus dem Rechtskataster entfernt.", "The provision is removed from the legal register."),
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.remove", "Entfernen", "Remove"), true)) {
            try {
                getSelectedData().setDeleted(true);
                getSelectedData().commit();
                tab.getLegalCadastre().removeLegislation(tab.getLegalCadastreTable().getSelectionModel().getSelectedItem());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }


    public void createItem() {
        LegalCadastreTab tab = getActiveTab();
        try {
            JEVisClass legalPlanDirClass = getActivePlan().getObject().getDataSource().getJEVisClass(JC.IndexofLegalProvisions.IndexofLegalProvionsDirectory.Obligation.ObligationDirectory.name);
            JEVisClass legalClass = getActivePlan().getObject().getDataSource().getJEVisClass(JC.IndexofLegalProvisions.IndexofLegalProvionsDirectory.Obligation.name);
            JEVisObject legalPlanDirObj = null;
            if (getActivePlan().getObject().getChildren(legalPlanDirClass, false).isEmpty()) {
                legalPlanDirObj = getActivePlan().getObject().buildObject(legalPlanDirClass.getName(), legalPlanDirClass);

                legalPlanDirObj.commit();
            } else {
                legalPlanDirObj = getActivePlan().getObject().getChildren(legalPlanDirClass, false).get(0);
            }

            int nextLegalNr = getActivePlan().getNextLegalNr();

            JEVisObject legalObject = legalPlanDirObj.buildObject(String.valueOf(nextLegalNr), legalClass);
            legalObject.commit();
            ObligationData obligationData = new ObligationData(legalObject, tab.getLegalCadastre());
            obligationData.commit();
            obligationData.nrProperty().set(nextLegalNr);
            tab.getLegalCadastre().addLegislation(obligationData);

            tab.getLegalCadastreTable().getSelectionModel().select(obligationData);
            openDataForm(true);//tab.getActionTable().getSelectionModel().getSelectedItem()
        } catch (Exception ex) {
            logger.error(ex);
        }
    }

    public void openPlanSettings() {
        LegalCadastreForm cadastreForm = new LegalCadastreForm(getActiveTab().getLegalCadastre());
        Optional<ButtonType> result = cadastreForm.showAndWait();
        if (result.isPresent() && result.get().getButtonData() == ButtonBar.ButtonData.OTHER) {
            getActiveTab().getLegalCadastre().commit();
        }

    }

    public void loadLegalPlans() {
        try {
            JEVisClass indexOfLegalProvisionsClass = plugin.getDataSource().getJEVisClass(JC.IndexofLegalProvisions.name);
            List<JEVisObject> indexOfLegalProvisionPlans = plugin.getDataSource().getObjects(indexOfLegalProvisionsClass, true);


            AtomicBoolean isFirstPlan = new AtomicBoolean(true);

            indexOfLegalProvisionPlans.forEach(jeVisObject -> {
                IndexOfLegalProvisions plan = new IndexOfLegalProvisions(jeVisObject);
                indexOfLegalProvisions.add(plan);
                if (isFirstPlan.get()) plan.loadLegalList();
                isFirstPlan.set(false);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Node getContent() {
        return contentPane;
    }

    public LegalCadastreTab getActiveTab() {
        LegalCadastreTab tab = (LegalCadastreTab) tabPane.getSelectionModel().getSelectedItem();
        return tab;
    }

    public IndexOfLegalProvisions getActivePlan() {
        return getActiveTab().getLegalCadastre();
    }

    public ObligationData getSelectedData() {
        LegalCadastreTab tab = getActiveTab();
        if (getActiveTab().getLegalCadastreTable().getSelectionModel().getSelectedItem() != null) {
            return getActiveTab().getLegalCadastreTable().getSelectionModel().getSelectedItem();
        } else {
            if (tab.getLegalCadastreTable().getItems().isEmpty()) {
                {
                    return null;
                }
            }

            return tab.getLegalCadastreTable().getItems().get(0);
        }
    }

    public void openDataForm(boolean isNew) {
        ObligationForm obligationForm = new ObligationForm(getActivePlan());
        ObligationData data = getSelectedData();
        obligationForm.setData(data);
        obligationForm.setNew(isNew);
        obligationForm.setTitle(I18n.getInstance().getString("plugin.indexoflegalprovisions.obligation.dialog.title"));
        obligationForm.setDescription(I18n.getInstance().getString("plugin.indexoflegalprovisions.obligation.dialog.header"));
        ButtonType buttonTypeOne = new ButtonType(I18n.getInstance().getString("plugin.indexoflegalprovisions.form.save"), ButtonBar.ButtonData.OTHER);
        ButtonType buttonTypeTwo = new ButtonType(I18n.getInstance().getString("plugin.indexoflegalprovisions.form.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);


        obligationForm.getDialogPane().getButtonTypes().setAll(buttonTypeOne, buttonTypeTwo);
        /* J25.5: Sun-coloured primary button, but no default button: Enter in a text field of the form must not save */
        obligationForm.getDialogPane().lookupButton(buttonTypeOne).getStyleClass().add("primary");
        final Button btOk = (Button) obligationForm.getDialogPane().lookupButton(buttonTypeOne);
        final Button btCancel = (Button) obligationForm.getDialogPane().lookupButton(buttonTypeTwo);
        btOk.setOnAction(actionEvent -> {
            try {
                data.commit();
                updateTrigger.set(!updateTrigger.get());

            } catch (Exception e) {
                logger.error(e);
            }
        });
        btCancel.addEventFilter(ActionEvent.ACTION, event -> {
            if (obligationForm.isNew()) {
                try {
                    data.getObject().delete();
                    getActiveTab().getLegalCadastre().getLegislationDataList().remove(data);

                } catch (Exception e) {
                    logger.error(e);
                }
            } else {
                reload(data);
            }
        });
        obligationForm.show();


    }

    private void reload(ObligationData obligationData) {
        try {
            obligationData = getActivePlan().loadObligations(obligationData.getObject());
        } catch (Exception e) {
            logger.error(e);
        }
    }


    public ObservableList<IndexOfLegalProvisions> getLegalCadastres() {
        return indexOfLegalProvisions;
    }

    public void setLegalCadastres(ObservableList<IndexOfLegalProvisions> indexOfLegalProvisions) {
        this.indexOfLegalProvisions = indexOfLegalProvisions;
    }
}
