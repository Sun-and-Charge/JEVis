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
package org.jevis.jeconfig.plugin.meters.ui;


import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.DatabaseHelper;
import org.jevis.commons.JEVisFileImp;
import org.jevis.commons.classes.JC;
import org.jevis.commons.constants.GUIConstants;
import org.jevis.commons.object.plugin.TargetHelper;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.application.application.I18nWS;
import org.jevis.jeconfig.application.jevistree.UserSelection;
import org.jevis.jeconfig.application.jevistree.filter.JEVisTreeFilter;
import org.jevis.jeconfig.dialog.SelectTargetDialog;
import org.jevis.jeconfig.application.ui.FormLayout;
import org.jevis.jeconfig.plugin.meters.data.JEVisTypeWrapper;
import org.jevis.jeconfig.plugin.meters.data.MeterData;
import org.jevis.jeconfig.plugin.meters.data.SampleData;
import org.jevis.jeconfig.plugin.object.attribute.GPSEditor;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import static org.jevis.commons.constants.GUIConstants.GPS;

public class MeterFormAttributeTab extends Tab implements MeterFormTab {

    private static final Logger logger = LogManager.getLogger(MeterForm.class);
    private final MeterData meterData;
    private final JEVisDataSource ds;
    private final JEVisTypeWrapper targetType;

    private final DateTime commitDateTime;
    /* type name -> {caption, control} */
    private final Map<String, Object[]> controls = new TreeMap<>();
    private final Map<String, Integer> positions = new HashMap<>();
    private final javafx.beans.property.BooleanProperty changed = new javafx.beans.property.SimpleBooleanProperty(false);
    private FormLayout form;
    Map<JEVisType, JEVisSample> newSamples = new HashMap<JEVisType, JEVisSample>() {
        @Override
        public JEVisSample put(JEVisType key, JEVisSample value) {
            changed.set(true);
            return super.put(key, value);
        }
    };


    public MeterFormAttributeTab(MeterData meterData, JEVisDataSource ds, String name) {
        super(name);
        this.meterData = meterData;
        this.ds = ds;
        this.targetType = new JEVisTypeWrapper(getJEVisType(JC.MeasurementInstrument.a_OnlineID));
        this.commitDateTime = DateTime.now();

        for (Map.Entry<JEVisTypeWrapper, SampleData> entry : meterData.getJeVisAttributeJEVisSampleMap().entrySet()) {
            JEVisType jeVisType = entry.getKey().getJeVisType();

            try {
                if (jeVisType.getPrimitiveType() == JEVisConstants.PrimitiveType.FILE && !jeVisType.getGUIDisplayType().equals(GPS.getId())) {
                    buildFileChooser(meterData, jeVisType, entry.getValue().getOptionalJEVisSample());
                } else if (jeVisType.getPrimitiveType() == JEVisConstants.PrimitiveType.FILE && jeVisType.getGUIDisplayType().equals(GPS.getId())) {
                    GPSEditor gpsEditor = new GPSEditor(entry.getValue().getJeVisAttribute());
                    gpsEditor.getValueChangedProperty().addListener((observableValue, aBoolean, t1) -> {
                        {
                            if (t1) {
                                try {
                                    newSamples.put(jeVisType, meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, gpsEditor.getNewFile()));
                                } catch (JEVisException | IOException e) {
                                    logger.error(e);
                                }
                                gpsEditor.setChanged(false);
                            }
                        }
                    });
                    put(jeVisType, I18nWS.getInstance().getTypeName(jeVisType), gpsEditor.getEditor());
                } else if (jeVisType.getGUIDisplayType().equals(GUIConstants.DATE_TIME.getId()) || jeVisType.getGUIDisplayType().equals(GUIConstants.BASIC_TEXT_DATE_FULL.getId())) {
                    buildCal(meterData, jeVisType, entry.getValue().getOptionalJEVisSample());
                } else if (jeVisType.getName().equals("Online ID")) {
                    buildTargetSelect(meterData, jeVisType, entry.getValue().getOptionalJEVisSample());
                } else if (GUIConstants.BASIC_TEXT_MULTI.getId().equals(jeVisType.getGUIDisplayType()) || jeVisType.getName().equals("Remarks")) {
                    buildTextArea(meterData, jeVisType, entry.getValue().getOptionalJEVisSample());
                } else {
                    buildTextField(meterData, jeVisType, entry.getValue().getOptionalJEVisSample());
                }
            } catch (JEVisException e) {
                logger.error(e);
                org.jevis.jeconfig.application.ui.Notify.error(org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.error.generic", "Das hat nicht geklappt. Verbindung prüfen und erneut versuchen.", "That did not work. Check the connection and try again."));
            }
        }

        form = buildForm();
        ScrollPane scrollPane = new ScrollPane(form);
        scrollPane.setFitToWidth(true);
        setContent(scrollPane);
    }

    private void put(JEVisType jeVisType, String caption, Node control) {
        String name;
        try {
            name = jeVisType.getName();
        } catch (Exception e) {
            name = caption;
        }
        controls.put(name, new Object[]{caption, control});
        positions.put(name, getGuiPosition(jeVisType));
    }

    /* sections of the meter form by attribute name; everything else goes to "Weitere Angaben" */
    private static final String[] IDENTIFICATION = {"Meter Point", "Measuring Point ID", "Device Number", "Serial Number", "Type", "Company", "Online ID"};
    private static final String[] VERIFICATION = {"Installation Date", "Verified Date", "Verification Date", "Accuracy", "Conversion Factor", "Decimal Places"};
    private static final String[] DOCUMENTS = {"Datasheet", "Verification Documentation", "Picture", "Reading Photo"};
    private static final String[] LOCATION = {"Location", "Installation Location", "Cost Center", "Connection", "Device IP", "GPS Position"};

    private FormLayout buildForm() {
        FormLayout layout = new FormLayout();
        Set<String> used = new HashSet<>();
        addSection(layout.section(text("plugin.meters.form.identification", "Kennung", "Identification")), IDENTIFICATION, used);
        addSection(layout.section(text("plugin.meters.form.verification", "Einbau und Prüfung", "Installation and verification")), VERIFICATION, used);
        FormLayout.Section documents = layout.section(text("plugin.meters.form.documents", "Dokumente und Fotos", "Documents and photos"));
        for (String name : DOCUMENTS) {
            Object[] c = controls.get(name);
            if (c != null) {
                documents.addWide((String) c[0], (Node) c[1]);
                used.add(name);
            }
        }
        Object[] remarks = controls.get("Remarks");
        if (remarks != null) {
            layout.section((String) remarks[0]).addWide("", (Node) remarks[1]);
            used.add("Remarks");
        }
        addSection(layout.collapsed(text("plugin.meters.form.location", "Ort und Anschluss", "Location and connection")), LOCATION, used);
        List<String> rest = new ArrayList<>();
        for (String name : controls.keySet()) if (!used.contains(name)) rest.add(name);
        rest.sort(Comparator.comparing(name -> positions.getOrDefault(name, 0)));
        if (!rest.isEmpty()) {
            addSection(layout.collapsed(text("plugin.meters.form.more", "Weitere Angaben", "More details")), rest.toArray(new String[0]), used);
        }
        return layout;
    }

    private void addSection(FormLayout.Section section, String[] names, Set<String> used) {
        for (String name : names) {
            Object[] c = controls.get(name);
            if (c == null) continue;
            if (c[1] instanceof TextArea) section.addWide((String) c[0], (Node) c[1]);
            else section.add((String) c[0], (Node) c[1]);
            used.add(name);
        }
    }

    private static String text(String key, String german, String english) {
        return org.jevis.jeconfig.application.navigation.PluginSidebar.text(key, german, english);
    }

    /** the form for the side panel (the tab shows the same form in the meter change dialog) */
    public FormLayout getForm() {
        return form;
    }

    /** true after the first change */
    public javafx.beans.property.BooleanProperty changedProperty() {
        return changed;
    }

    public void commit() {
        try {
            for (Map.Entry<JEVisType, JEVisSample> entry : newSamples.entrySet()) {
                entry.getValue().commit();
            }
        } catch (JEVisException e) {
            logger.error(e);
        }

    }

    private JEVisType getJEVisType(String string) {
        try {
            JEVisClass jeVisClass = ds.getJEVisClass(JC.MeasurementInstrument.name);
            JEVisType jeVisType = jeVisClass.getType(string);
            return jeVisType;

        } catch (Exception e) {
            logger.error(e);
        }
        return null;


    }

    private void buildFileChooser(MeterData meterData, JEVisType jeVisType, Optional<JEVisSample> optionalJEVisSample) {
        String caption = "";
        try {
            caption = I18nWS.getInstance().getTypeName(jeVisType);
        } catch (Exception e) {
            logger.error(e);
        }
        final FormLayout.FileRow[] row = new FormLayout.FileRow[1];
        Runnable download = optionalJEVisSample.isPresent() ? () -> {
            try {
                JEVisFile file = optionalJEVisSample.get().getValueAsFile();
                FileChooser chooser = new FileChooser();
                chooser.setInitialFileName(file.getFilename());
                File target = chooser.showSaveDialog(JEConfig.getStage());
                if (target != null) {
                    file.saveToFile(target);
                    JEConfig.setLastPath(target);
                }
            } catch (Exception ex) {
                logger.error(ex);
                org.jevis.jeconfig.application.ui.Notify.error(text("dialog.ui.error.generic", "Das hat nicht geklappt. Verbindung prüfen und erneut versuchen.", "That did not work. Check the connection and try again."));
            }
        } : null;
        Runnable upload = () -> {
            FileChooser fileChooser = new FileChooser();
            File selectedFile = fileChooser.showOpenDialog(JEConfig.getStage());
            if (selectedFile != null) {
                try {
                    JEConfig.setLastPath(selectedFile);
                    JEVisFile jfile = new JEVisFileImp(selectedFile.getName(), selectedFile);
                    newSamples.put(jeVisType, meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, jfile));
                    row[0].setFileName(selectedFile.getName(), false);
                } catch (Exception ex) {
                    logger.catching(ex);
                }
            }
        };
        String name = "";
        try {
            name = optionalJEVisSample.isPresent() ? optionalJEVisSample.get().getValueAsString() : "";
        } catch (Exception e) {
            logger.error(e);
        }
        row[0] = FormLayout.fileRow(name, download, upload);
        put(jeVisType, caption, row[0]);
    }

    private int getGuiPosition(JEVisType jeVisType) {
        try {
            return jeVisType.getGUIPosition();
        } catch (JEVisException jeVisException) {
            logger.error(jeVisException);
        }
        return 0;
    }

    private void buildCal(MeterData meterData, JEVisType jeVisType, Optional<JEVisSample> optionalJEVisSample) {
        Label label = new Label();
        DatePicker jfxDatePicker = new DatePicker();
        jfxDatePicker.getEditor().setAlignment(Pos.CENTER_RIGHT);
        JEVisObject jeVisObject = meterData.getJeVisObject();
        try {
            label.setText(I18nWS.getInstance().getTypeName(jeVisType));
            DateTime dateTime = DatabaseHelper.getObjectAsDate(jeVisObject, jeVisType);
            jfxDatePicker.setValue(toLocalDate(dateTime));
            jfxDatePicker.valueProperty().addListener((observableValue, localDate, t1) -> {

                try {
                    newSamples.put(jeVisType, jeVisObject.getAttribute(
                            jeVisType).buildSample(commitDateTime, toDateTime(t1)));
                } catch (JEVisException e) {
                    logger.error(e);
                    org.jevis.jeconfig.application.ui.Notify.error(org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.error.save", "Der Wert konnte nicht gespeichert werden. Verbindung prüfen und erneut versuchen.", "The value could not be saved. Check the connection and try again."));

                }
            });

        } catch (Exception e) {
            logger.error(e);
        }

        jfxDatePicker.getEditor().setAlignment(Pos.CENTER_LEFT);
        put(jeVisType, label.getText(), jfxDatePicker);
    }

    private LocalDate toLocalDate(DateTime dateTime) {
        DateTime dateTimeUtc = dateTime.withZone(DateTimeZone.UTC);
        return LocalDate.of(dateTimeUtc.getYear(), dateTimeUtc.getMonthOfYear(), dateTimeUtc.getDayOfMonth());
    }

    public DateTime toDateTime(LocalDate localDate) {
        return new DateTime(DateTimeZone.UTC).withDate(
                localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth()
        ).withTime(0, 0, 0, 0);
    }

    public void buildTargetSelect(MeterData meterData, JEVisType jeVisType, Optional<JEVisSample> optionalJEVisSample) {

        Label label = new Label();
        Label linkName = new Label();
        Button jfxButton = new Button("", JEConfig.getSVGImage(Icon.TREE, 18, 18));
        jfxButton.setAlignment(Pos.CENTER_RIGHT);

        try {
            label.setText(I18nWS.getInstance().getTypeName(jeVisType));

            TargetHelper th = null;
            try {
                th = new TargetHelper(ds, meterData.getJeVisObject().getAttribute(targetType.getJeVisType()));
            } catch (JEVisException e) {
                logger.error(e);
            }

            if (th.isValid() && th.targetObjectAccessible()) {
                logger.info("Target is valid");

                linkName.setText(th.getObject().get(0).getName());
            }
            TargetHelper finalTh = th;

            jfxButton.setOnAction(actionEvent -> {

                List<JEVisTreeFilter> allFilter = new ArrayList<>();
                JEVisTreeFilter allDataFilter = SelectTargetDialog.buildAllDataFilter();
                allFilter.add(allDataFilter);
                List<UserSelection> openList = new ArrayList<>();
                if (!finalTh.getObject().isEmpty()) {
                    for (JEVisObject obj : finalTh.getObject()) {
                        openList.add(new UserSelection(UserSelection.SelectionType.Object, obj));
                    }
                }
                SelectTargetDialog selectTargetDialog = new SelectTargetDialog(allFilter, allDataFilter, null, SelectionMode.SINGLE, ds, openList);
                selectTargetDialog.setOnCloseRequest(event1 -> {
                    try {
                        if (selectTargetDialog.getResponse() == SelectTargetDialog.Response.OK) {
                            logger.trace("Selection Done");

                            String newTarget = "";
                            List<UserSelection> selections = selectTargetDialog.getUserSelection();
                            for (UserSelection us : selections) {
                                int index = selections.indexOf(us);
                                if (index > 0) newTarget += ";";

                                newTarget += us.getSelectedObject().getID();
                                linkName.setText(us.getSelectedObject().getName());
                                if (us.getSelectedAttribute() != null) {
                                    newTarget += ":" + us.getSelectedAttribute().getName();

                                } else {
                                    newTarget += ":Value";
                                }
                            }
                            JEVisSample newTargetSample = meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, newTarget);
                            newSamples.put(jeVisType, newTargetSample);
                        }

                    } catch (JEVisException e) {
                        logger.error(e);
                        org.jevis.jeconfig.application.ui.Notify.error(org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.error.generic", "Das hat nicht geklappt. Verbindung prüfen und erneut versuchen.", "That did not work. Check the connection and try again."));
                    }
                });
                selectTargetDialog.showAndWait();
            });

        } catch (Exception e) {
            logger.error(e);
            org.jevis.jeconfig.application.ui.Notify.error(org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.error.generic", "Das hat nicht geklappt. Verbindung prüfen und erneut versuchen.", "That did not work. Check the connection and try again."));
        }

        HBox target = new HBox(8, jfxButton, linkName);
        target.setAlignment(Pos.CENTER_LEFT);
        jfxButton.setTooltip(new Tooltip(text("plugin.meters.form.target", "Datenreihe wählen", "Choose data row")));
        put(jeVisType, label.getText(), target);
    }

    private void buildTextArea(MeterData meterData, JEVisType jeVisType, Optional<JEVisSample> optionalJEVisSample) {
        Label label = new Label();
        TextArea textArea = new TextArea();
        textArea.setPrefWidth(200);
        try {
            label.setText(I18nWS.getInstance().getTypeName(jeVisType));
            if (optionalJEVisSample.isPresent()) {
                textArea.setText(optionalJEVisSample.get().getValueAsString());
            }

            textArea.textProperty().addListener((observableValue, s, t1) -> {
                int primitiveType = 0;
                try {
                    primitiveType = jeVisType.getPrimitiveType();
                } catch (JEVisException e) {
                    logger.error(e);
                    return;
                }
                if (t1.isEmpty()) {
                    try {
                        newSamples.put(jeVisType, meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, ""));
                    } catch (Exception e) {
                        logger.error(e);
                    }
                } else
                    try {
                        switch (primitiveType) {
                            case JEVisConstants.PrimitiveType.STRING:
                                String str = t1;
                                newSamples.put(jeVisType, meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, str));
                                break;
                            case JEVisConstants.PrimitiveType.LONG:
                                Long l = Long.valueOf(t1);
                                newSamples.put(jeVisType, meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, l));
                                break;
                            case JEVisConstants.PrimitiveType.BOOLEAN:
                                Boolean b = Boolean.valueOf(t1);
                                newSamples.put(jeVisType, meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, b));
                                break;
                            case JEVisConstants.PrimitiveType.DOUBLE:
                                Double d = Double.valueOf(t1);
                                newSamples.put(jeVisType, meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, d));
                                break;
                        }
                    } catch (NumberFormatException e) {
                        logger.error(e);
                        org.jevis.jeconfig.application.ui.Notify.error(org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.error.number", "Bitte eine Zahl eingeben, z. B. 12,5.", "Please enter a number, e.g. 12.5."));
                    } catch (JEVisException e) {
                        logger.error(e);
                        org.jevis.jeconfig.application.ui.Notify.error(org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.error.generic", "Das hat nicht geklappt. Verbindung prüfen und erneut versuchen.", "That did not work. Check the connection and try again."));
                    }

            });


        } catch (JEVisException e) {
            logger.error(e);
            return;
        }

        textArea.setPrefRowCount(4);
        textArea.setWrapText(true);
        put(jeVisType, label.getText(), textArea);
    }

    private void buildTextField(MeterData meterData, JEVisType jeVisType, Optional<JEVisSample> optionalJEVisSample) {
        Label label = new Label();
        TextField textField = new TextField();
        textField.setPrefWidth(200);

        try {
            label.setText(I18nWS.getInstance().getTypeName(jeVisType));
            if (optionalJEVisSample.isPresent()) {
                textField.setText(optionalJEVisSample.get().getValueAsString());
            }

            textField.textProperty().addListener((observableValue, s, t1) -> {
                int primitiveType = 0;
                try {
                    primitiveType = jeVisType.getPrimitiveType();
                } catch (Exception e) {
                    logger.error(e);
                    return;
                }
                if (t1.isEmpty()) {
                    try {
                        newSamples.put(jeVisType, meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, ""));
                    } catch (Exception e) {
                        logger.error(e);
                    }
                } else
                    try {
                        switch (primitiveType) {
                            case JEVisConstants.PrimitiveType.STRING:
                                String str = t1;
                                newSamples.put(jeVisType, meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, str));
                                break;
                            case JEVisConstants.PrimitiveType.LONG:
                                Long l = Long.valueOf(t1);
                                newSamples.put(jeVisType, meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, l));
                                break;
                            case JEVisConstants.PrimitiveType.BOOLEAN:
                                Boolean b = Boolean.valueOf(t1);
                                newSamples.put(jeVisType, meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, b));
                                break;
                            case JEVisConstants.PrimitiveType.DOUBLE:
                                Double d = Double.valueOf(t1);
                                newSamples.put(jeVisType, meterData.getJeVisObject().getAttribute(jeVisType).buildSample(commitDateTime, d));
                                break;
                        }
                    } catch (NumberFormatException e) {
                        logger.error(e);
                        org.jevis.jeconfig.application.ui.Notify.error(org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.error.number", "Bitte eine Zahl eingeben, z. B. 12,5.", "Please enter a number, e.g. 12.5."));
                    } catch (Exception e) {
                        logger.error(e);
                        org.jevis.jeconfig.application.ui.Notify.error(org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.error.generic", "Das hat nicht geklappt. Verbindung prüfen und erneut versuchen.", "That did not work. Check the connection and try again."));
                    }
            });


        } catch (Exception e) {
            logger.error(e);
            return;
        }

        put(jeVisType, label.getText(), textField);
    }

}
