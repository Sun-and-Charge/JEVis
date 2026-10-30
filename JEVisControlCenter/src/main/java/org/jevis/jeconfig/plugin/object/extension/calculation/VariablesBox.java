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
package org.jevis.jeconfig.plugin.object.extension.calculation;

import com.jfoenix.controls.JFXButton;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.FlowPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.classes.JC;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.application.jevistree.JEVisTree;
import org.jevis.jeconfig.application.jevistree.TreeHelper;

import java.util.ArrayList;
import java.util.List;

public class VariablesBox extends FlowPane {


    private static final Logger logger = LogManager.getLogger(VariablesBox.class);
    private final List<JEVisObject> variables = new ArrayList<>();
    private FormulaBox expression;
    private JEVisTree tree;

    public VariablesBox() {
        super();
//        setSpacing(5);
        setVgap(8);
        setHgap(4);
        setPrefWrapLength(200);

    }

    public void setTree(JEVisTree tree) {
        this.tree = tree;
    }

    public void bindVariableBox(FormulaBox expression, JEVisObject obj) {
        this.expression = expression;
        listVariables(obj);
    }


    private void buildVarButton(JEVisObject inputObject) {

        try {
            JFXButton button = new JFXButton();
            button.setFocusTraversable(false);
            JEVisAttribute id = inputObject.getAttribute("Identifier");
            if (id != null) {
                JEVisSample value = id.getLatestSample();
                if (value != null && !value.getValueAsString().isEmpty()) {
                    String name = value.getValueAsString();
                    button.setText(name);

                    button.setOnAction(event -> expression.addExpression("#{" + name + "}"));
                } else {
                    button.setText("*no name* ID: " + inputObject.getID());
                    button.disableProperty().setValue(true);
                }

                if (tree != null) {
                    MenuItem gotoItem = new MenuItem(I18n.getInstance().getString("plugin.object.attribute.target.goto.tooltip"));
                    gotoItem.setOnAction(event -> {
                        try {
                            FormulaBox.openTargetInTree(tree, inputObject.getAttribute(JC.Input.a_InputData));
                        } catch (Exception ex) {
                            logger.error("Could not open input in tree", ex);
                        }
                    });
                    button.setContextMenu(new ContextMenu(gotoItem));
                }

                getChildren().add(button);
            }

        } catch (Exception np) {
            np.printStackTrace();
        }
    }

    public void listVariables(JEVisObject obj) {
        getChildren().clear();

        JFXButton addInputButton = new JFXButton("", JEConfig.getSVGImage(Icon.PLUS_CIRCLE, 15, 15));
        addInputButton.setOnAction(event -> {
            try {
                TreeHelper.createCalcInput(obj, null, this, expression);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        getChildren().add(addInputButton);

        try {
            JEVisClass inputClass = obj.getDataSource().getJEVisClass(JC.Input.name);
            for (JEVisObject var : obj.getChildren()) {
                if (var.getJEVisClass().equals(inputClass)) {
                    buildVarButton(var);
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
