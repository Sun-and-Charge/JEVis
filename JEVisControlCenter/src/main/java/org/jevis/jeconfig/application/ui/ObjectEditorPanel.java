/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEConfig.
 * <p>
 * JEConfig is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEConfig is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEConfig. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEConfig is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.application.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;
import org.jevis.jeconfig.application.application.I18nWS;
import org.jevis.jeconfig.plugin.object.attribute.AttributeEditor;
import org.jevis.jeconfig.plugin.object.extension.GenericAttributeExtension;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic editor of one object in the side panel: name and all attributes with the attribute editors of the object
 * tree, laid out by {@link FormLayout} (labels above the fields, two columns, long editors over both columns).
 * Used where a module has no own form (e.g. equipment).
 */
public final class ObjectEditorPanel {

    private static final Logger logger = LogManager.getLogger(ObjectEditorPanel.class);

    private ObjectEditorPanel() {
    }

    /**
     * @param afterSave runs after a successful save (e.g. reload the table); may be null
     */
    public static void open(JEVisObject object, String subtitle, Runnable afterSave) {
        List<AttributeEditor> editors = new ArrayList<>();
        BooleanProperty nameChanged = new SimpleBooleanProperty(false);
        FormLayout form = new FormLayout();

        TextField name = new TextField(object.getName());
        name.textProperty().addListener((o, a, b) -> nameChanged.set(!b.equals(object.getName())));
        form.section(SucDialog.text("dialog.ui.form.general", "Allgemein", "General"))
                .addWide(SucDialog.text("newobject.name.label", "Name", "Name"), name);

        FormLayout.Section details = form.section(SucDialog.text("dialog.ui.form.details", "Angaben", "Details"));
        boolean canWrite = false;
        try {
            canWrite = object.getDataSource().getCurrentUser().canWrite(object.getID());
            for (JEVisAttribute attribute : object.getAttributes()) {
                AttributeEditor editor = GenericAttributeExtension.getEditor(attribute.getType(), attribute);
                if (editor == null) continue;
                editor.setReadOnly(!canWrite);
                editors.add(editor);
                Node node = editor.getEditor();
                String caption = I18nWS.getInstance().getTypeName(attribute.getType());
                if (node instanceof TextArea || node.prefWidth(-1) > 260) {
                    details.addWide(caption, node);
                } else {
                    details.add(caption, node);
                }
            }
        } catch (Exception e) {
            logger.error("Could not build the editor of {}", object.getID(), e);
            Notify.error(SucDialog.text("dialog.ui.error.generic", "Das hat nicht geklappt. Verbindung prüfen und erneut versuchen.", "That did not work. Check the connection and try again."));
            return;
        }

        SidePanel panel = SidePanel.get();
        boolean shown = panel.show(object.getName(), subtitle, form, () -> {
            String newName = name.getText().trim();
            if (newName.isEmpty()) {
                Notify.error(SucDialog.text("dialog.ui.error.name", "Bitte einen Namen eingeben.", "Please enter a name."));
                return false;
            }
            for (AttributeEditor editor : editors) {
                if (!editor.isValid()) {
                    Notify.error(SucDialog.text("dialog.ui.error.field", "Ein Wert ist ungültig. Bitte das markierte Feld prüfen.", "A value is not valid. Please check the marked field."));
                    return false;
                }
            }
            try {
                for (AttributeEditor editor : editors) {
                    if (editor.hasChanged()) editor.commit();
                }
                if (!newName.equals(object.getName())) {
                    object.setName(newName);
                    object.commit();
                }
            } catch (Exception e) {
                logger.error("Could not save {}", object.getID(), e);
                Notify.error(SucDialog.text("dialog.ui.error.save", "Der Wert konnte nicht gespeichert werden. Verbindung prüfen und erneut versuchen.", "The value could not be saved. Check the connection and try again."));
                return false;
            }
            Notify.success(SucDialog.text("dialog.ui.saved", "Gespeichert", "Saved"));
            if (afterSave != null) afterSave.run();
            return true;
        });
        if (!shown) return;
        BooleanBinding changed = Bindings.createBooleanBinding(() -> nameChanged.get() || editors.stream().anyMatch(AttributeEditor::hasChanged),
                editors.stream().map(AttributeEditor::getValueChangedProperty).toArray(javafx.beans.Observable[]::new));
        BooleanBinding any = changed.or(nameChanged);
        panel.dirtyProperty().bind(any);
        panel.setReadOnly(!canWrite);
    }
}
