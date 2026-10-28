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
package org.jevis.jeconfig.plugin.nonconformities.ui;

import javafx.scene.control.Dialog;
import javafx.scene.control.TabPane;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.plugin.nonconformities.data.NonconformityData;
import org.jevis.jeconfig.plugin.nonconformities.data.NonconformityPlan;
import org.jevis.jeconfig.plugin.nonconformities.ui.tab.AttachmentTab;
import org.jevis.jeconfig.plugin.nonconformities.ui.tab.CheckListTab;
import org.jevis.jeconfig.plugin.nonconformities.ui.tab.GeneralTab;
import org.jevis.jeconfig.tool.ScreenSize;

public class NonconformityForm extends Dialog {

    private boolean isNew;

    public NonconformityForm() {
    }


    public TabPane tabPane = new TabPane();


    private GeneralTab basicTab = new GeneralTab(I18n.getInstance().getString("plugin.nonconformities.form.tab.general"));
    // private Tab detailTab = new Tab(I18n.getInstance().getString("actionform.editor.tab.deteils"));
    private CheckListTab checkListTab = new CheckListTab(I18n.getInstance().getString("plugin.nonconformities.form.tab.checklist"));

    private AttachmentTab attachmentTab = new AttachmentTab(I18n.getInstance().getString("plugin.nonconformities.form.tab.attachment"));

    private NonconformityPlan nonconformityPlan;


    public NonconformityForm(NonconformityPlan nonconformityPlan) {

        super();
        this.initOwner(JEConfig.getStage());
        this.nonconformityPlan = nonconformityPlan;


        setTitle(I18n.getInstance().getString("plugin.nonconformities.nonconfromityform.editor.title"));
        setHeaderText(null);
        setResizable(true);
        this.getDialogPane().setPrefWidth(ScreenSize.fitScreenWidth(1000));
        this.getDialogPane().setPrefHeight(ScreenSize.fitScreenHeight(950));

        widthProperty().addListener((observable, oldValue, newValue) -> tabPane.setPrefWidth(newValue.doubleValue() - 50));


        tabPane.setPrefWidth(ScreenSize.fitScreenWidth(1050));


        basicTab.setClosable(false);

        attachmentTab.setClosable(false);


        tabPane.getTabs().addAll(basicTab, checkListTab, attachmentTab);
        getDialogPane().setContent(tabPane);
    }

    public void setData(NonconformityData data) {
        updateView(data);
        basicTab.initTab(data);
        checkListTab.initTab(data);
        attachmentTab.initTab(data);
    }


    private void updateView(NonconformityData data) {

        checkListTab.updateView(data);
        basicTab.updateView(data);
        attachmentTab.updateView(data);

    }
    public void showNotification(String text, String path_icon) {
        basicTab.getF_ImmediateMeasures().getStyleClass().set(0,"nonconformityOK");
        basicTab.getF_action().getStyleClass().set(0,"nonconformityOK");
        basicTab.getF_doneDate().getStyleClass().set(0,"nonconformityOK");
        if (text.equals(NonconformityData.IMMEDIATE_ACTION)) {
            basicTab.getF_ImmediateMeasures().getStyleClass().set(0,"nonconformityError");
        } else if (text.equals(NonconformityData.DONE_DATE_ACTION)) {
            basicTab.getF_action().getStyleClass().set(0,"nonconformityError");
        } else if (text.equals(NonconformityData.DONE_DATE_AFTER_NOW)) {
            basicTab.getF_doneDate().getStyleClass().set(0,"nonconformityError");
        }

        tabPane.getSelectionModel().select(basicTab);
        attachmentTab.showNotification(text,JEConfig.getSVGImage(path_icon,24,24));
        basicTab.showNotification(text,JEConfig.getSVGImage(path_icon,24,24));
        checkListTab.showNotification(text,JEConfig.getSVGImage(path_icon,24,24));

    }


    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }
}
