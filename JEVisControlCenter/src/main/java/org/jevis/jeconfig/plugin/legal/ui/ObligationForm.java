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
package org.jevis.jeconfig.plugin.legal.ui;

import javafx.scene.control.Dialog;
import javafx.scene.control.TabPane;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.plugin.legal.data.IndexOfLegalProvisions;
import org.jevis.jeconfig.plugin.legal.data.ObligationData;

import org.jevis.jeconfig.plugin.legal.ui.tab.AttachmentTab;
import org.jevis.jeconfig.plugin.legal.ui.tab.GeneralTab;
import org.jevis.jeconfig.tool.ScreenSize;

public class ObligationForm extends Dialog {

    private boolean isNew;

    public ObligationForm() {
    }


    public TabPane tabPane = new TabPane();


    private GeneralTab basicTab = new GeneralTab(I18n.getInstance().getString("plugin.indexoflegalprovisions.obligation.general"));

    private AttachmentTab attachmentTab = new AttachmentTab(I18n.getInstance().getString("plugin.indexoflegalprovisions.obligation.attachment"));

    private IndexOfLegalProvisions indexOfLegalProvisions;


    public ObligationForm(IndexOfLegalProvisions indexOfLegalProvisions) {

        super();
        this.initOwner(JEConfig.getStage());
        this.indexOfLegalProvisions = indexOfLegalProvisions;


        setTitle(I18n.getInstance().getString("plugin.indexoflegalprovisions.obligation.dialog.title"));
        setHeaderText(null);
        setResizable(true);
        this.getDialogPane().setPrefWidth(ScreenSize.fitScreenWidth(1000));
        this.getDialogPane().setPrefHeight(ScreenSize.fitScreenHeight(950));

        widthProperty().addListener((observable, oldValue, newValue) -> tabPane.setPrefWidth(newValue.doubleValue() - 50));


        tabPane.setPrefWidth(ScreenSize.fitScreenWidth(1050));


        basicTab.setClosable(false);

        attachmentTab.setClosable(false);


        tabPane.getTabs().addAll(basicTab, attachmentTab);
        getDialogPane().setContent(tabPane);
    }

    public void setData(ObligationData data) {
        updateView(data);
        basicTab.initTab(data);
        attachmentTab.initTab(data);
    }


    private void updateView(ObligationData data) {

        basicTab.updateView(data);
        attachmentTab.updateView(data);

    }


    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }
}
