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

import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisObject;
import org.jevis.jeconfig.application.jevistree.JEVisTree;


public class CalculationViewController {

    private static final Logger logger = LogManager.getLogger(CalculationViewController.class);

    @FXML
    public Button atan2;
    @FXML
    public ScrollPane variablesScrollPane;
    @FXML
    private Button buttonCeil;
    @FXML
    private Button buttonLog;
    @FXML
    private Button buttonDot;
    @FXML
    private Button buttonSeven;
    @FXML
    private Button buttonTwo;
    @FXML
    private Button buttonBracketOpen;
    @FXML
    private Button buttonThree;
    @FXML
    private Button buttonMultiply;
    @FXML
    private Button buttonAcos;
    @FXML
    private Button buttonNotEquals;
    @FXML
    private Button buttonRound;
    @FXML
    private TextField expressionField;
    @FXML
    private Button buttonZero;
    @FXML
    private Button buttonEquals;
    @FXML
    private Button buttonFour;
    @FXML
    private Button buttonDivide;
    @FXML
    private Button buttonAtan;
    @FXML
    private Button buttonGreater;
    @FXML
    private Button buttonClear;
    @FXML
    private Button buttonBracketClose;
    @FXML
    private Button buttonSqrt;
    @FXML
    private Button buttonE;
    @FXML
    private Button buttonRad;
    @FXML
    private Button buttonGreaterEquals;
    @FXML
    private Button buttonExp;
    @FXML
    private Button buttonPi;
    @FXML
    private Button buttonTan;
    @FXML
    private Button buttonSmaller;
    @FXML
    private Button buttonMod;
    @FXML
    private Button buttonOr;
    @FXML
    private Button buttonOutput;
    @FXML
    private Button buttonMinus;
    @FXML
    private Button buttonDeg;
    @FXML
    private Button buttonCos;
    @FXML
    private Button buttonFloor;
    @FXML
    private Button buttonAbs;
    @FXML
    private Button buttonSmallerEquals;
    @FXML
    private Button buttonEight;
    @FXML
    private Button buttonNine;
    @FXML
    private Button buttonFive;
    @FXML
    private Button buttonSix;
    @FXML
    private Button buttonMax;
    @FXML
    private Button buttonOne;
    @FXML
    private Button buttonBack;
    @FXML
    private Button buttonMin;
    @FXML
    private Button buttonAnd;
    @FXML
    private Button buttonPow;
    @FXML
    private Button buttonSin;
    @FXML
    private Button buttonAtan2;
    @FXML
    private ListView<?> listInputs;
    @FXML
    private Button buttonAsin;
    @FXML
    private FormulaBox formulaBox;
    @FXML
    private VariablesBox variablesBox;
    @FXML
    private Button buttonVerify;

    @FXML
    public void initialize() {
    }


    @FXML
    void onSeven(ActionEvent event) {
        formulaBox.addExpression("7");
    }

    @FXML
    void onEight(ActionEvent event) {
        formulaBox.addExpression("8");
    }

    @FXML
    void onOutput(ActionEvent event) {
        formulaBox.setOnOutputAction();
    }

    @FXML
    void onNine(ActionEvent event) {

        formulaBox.addExpression("9");
    }

    @FXML
    void onFour(ActionEvent event) {
        formulaBox.addExpression("4");
    }

    @FXML
    void onSix(ActionEvent event) {
        formulaBox.addExpression("6");
    }

    @FXML
    void onFive(ActionEvent event) {
        formulaBox.addExpression("5");
    }

    @FXML
    void onThree(ActionEvent event) {
        formulaBox.addExpression("3");
    }

    @FXML
    void onTwo(ActionEvent event) {
        formulaBox.addExpression("2");
    }

    @FXML
    void onOne(ActionEvent event) {
        formulaBox.addExpression("1");
    }

    @FXML
    void onZero(ActionEvent event) {
        formulaBox.addExpression("0");
    }

    @FXML
    void onMod(ActionEvent event) {
        formulaBox.addExpression("%");
    }

    @FXML
    void onModulo(ActionEvent event) {
        formulaBox.addExpression("%");
    }

    @FXML
    void onDivide(ActionEvent event) {

        formulaBox.addExpression("/");
    }

    @FXML
    void onMultiply(ActionEvent event) {

        formulaBox.addExpression("*");
    }

    @FXML
    void onMinus(ActionEvent event) {
        formulaBox.addExpression("-");
    }

    @FXML
    void onPlus(ActionEvent event) {
        formulaBox.addExpression("+");
    }

    @FXML
    void onDot(ActionEvent event) {
        formulaBox.addExpression(".");
    }

    @FXML
    void onBracketOpen(ActionEvent event) {
        formulaBox.addExpression("(");
    }

    @FXML
    void onPi(ActionEvent event) {
        formulaBox.addExpression("#{Pi}");
    }

    @FXML
    void onBracketClose(ActionEvent event) {
        formulaBox.addExpression(")");
    }

    @FXML
    void onE(ActionEvent event) {
        formulaBox.addExpression("#{E}");
    }

    @FXML
    void onDeg(ActionEvent event) {
        formulaBox.addExpression("deg()");
    }

    @FXML
    void onClear(ActionEvent event) {
        formulaBox.setExpression("");
    }

    @FXML
    void onBack(ActionEvent event) {
        formulaBox.backspaceExpression();
    }

    @FXML
    void onRad(ActionEvent event) {
        formulaBox.addExpression("rad()");
    }

    @FXML
    void onPow(ActionEvent event) {
        formulaBox.addExpression("^");
    }

    @FXML
    void onSqrt(ActionEvent event) {
        formulaBox.addExpression("sqrt()");
    }

    @FXML
    void onLog(ActionEvent event) {
        formulaBox.addExpression("log()");
    }

    @FXML
    void onMin(ActionEvent event) {
        formulaBox.addExpression("min()");
    }

    @FXML
    void onCeil(ActionEvent event) {
        formulaBox.addExpression("ceiling()");
    }

    @FXML
    void onAbs(ActionEvent event) {
        formulaBox.addExpression("abs()");
    }

    @FXML
    void onMax(ActionEvent event) {
        formulaBox.addExpression("max()");
    }

    @FXML
    void onRound(ActionEvent event) {
        formulaBox.addExpression("round()");
    }

    @FXML
    void onExp(ActionEvent event) {
        formulaBox.addExpression("e^()");
    }

    @FXML
    void onFloor(ActionEvent event) {
        formulaBox.addExpression("floor()");
    }

    @FXML
    void onSin(ActionEvent event) {
        formulaBox.addExpression("sin()");
    }

    @FXML
    void onCos(ActionEvent event) {
        formulaBox.addExpression("cos()");
    }

    @FXML
    void onTan(ActionEvent event) {
        formulaBox.addExpression("tan()");
    }

    @FXML
    void onAsin(ActionEvent event) {
        formulaBox.addExpression("asin()");
    }

    @FXML
    void onAcos(ActionEvent event) {
        formulaBox.addExpression("acos()");
    }

    @FXML
    void onAtan2(ActionEvent event) {
        formulaBox.addExpression("atan2()");
    }

    @FXML
    void onAtan(ActionEvent event) {
        formulaBox.addExpression("atan()");
    }

    @FXML
    void onSmaller(ActionEvent event) {
        formulaBox.addExpression("<");
    }

    @FXML
    void onSmallerEquals(ActionEvent event) {
        formulaBox.addExpression("<=");
    }

    @FXML
    void onGreaterEquals(ActionEvent event) {
        formulaBox.addExpression(">=");
    }

    @FXML
    void onGreater(ActionEvent event) {
        formulaBox.addExpression(">");
    }

    @FXML
    void onEquals(ActionEvent event) {
        formulaBox.addExpression("==");
    }

    @FXML
    void onNotEquals(ActionEvent event) {
        formulaBox.addExpression("!=");
    }

    @FXML
    void onOr(ActionEvent event) {
        formulaBox.addExpression("||");
    }

    @FXML
    void onAnd(ActionEvent event) {
        formulaBox.addExpression("&&");
    }

    @FXML
    void onExpressionField(ActionEvent event) {

    }

    @FXML
    void onVerify(ActionEvent event) {
        formulaBox.eval();
    }


    public void setData(JEVisObject obj, Button buttonOutput) {
        setData(obj, buttonOutput, null);
    }

    /**
     * @param tree the object tree for "go to" of inputs and output, may be {@code null}
     */
    public void setData(JEVisObject obj, Button buttonOutput, JEVisTree tree) {
        logger.info("setData: {}", buttonOutput);
        formulaBox.setTree(tree);
        variablesBox.setTree(tree);
        formulaBox.setCalculation(obj);

        formulaBox.setOutputButton(buttonOutput);
        variablesBox.bindVariableBox(formulaBox, obj);

        variablesScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        variablesScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    }


    public String getFormula() {
        return formulaBox.getExpression();
    }

}