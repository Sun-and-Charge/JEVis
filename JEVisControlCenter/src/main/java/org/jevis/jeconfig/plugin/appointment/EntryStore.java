/**
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
package org.jevis.jeconfig.plugin.appointment;

import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.joda.time.DateTime;

import java.util.*;

/**
 * Loading and saving of the JSON entries of the appointment (#1855) and maintenance (#2663) modules: one object per
 * entry, the entry as JSON in its "Data" attribute.
 */
public final class EntryStore {

    private static final Logger logger = LogManager.getLogger(EntryStore.class);
    private static final int MAX_NAME_LENGTH = 120;

    private EntryStore() {
    }

    /**
     * @return all readable objects of the class, with freshly loaded attributes
     */
    public static List<JEVisObject> load(JEVisDataSource ds, String className) throws JEVisException {
        JEVisClass jeVisClass = ds.getJEVisClass(className);
        if (jeVisClass == null) {
            logger.warn("Class {} does not exist on the server", className);
            return Collections.emptyList();
        }
        List<JEVisObject> objects = new ArrayList<>(ds.getObjects(jeVisClass, false));
        for (JEVisObject object : objects) {
            try {
                ds.reloadAttribute(object);
            } catch (Exception e) {
                logger.debug("Could not reload attributes of {}", object, e);
            }
        }
        return objects;
    }

    public static String read(JEVisObject object, String attributeName) {
        try {
            JEVisAttribute attribute = object.getAttribute(attributeName);
            if (attribute == null) {
                return null;
            }
            JEVisSample sample = attribute.getLatestSample();
            return sample != null ? sample.getValueAsString() : null;
        } catch (Exception e) {
            logger.warn("Could not read {} of {}", attributeName, object, e);
            return null;
        }
    }

    public static void write(JEVisObject object, String attributeName, String value) throws JEVisException {
        JEVisAttribute attribute = object.getAttribute(attributeName);
        if (attribute == null) {
            throw new JEVisException("Attribute '" + attributeName + "' missing in class " + object.getJEVisClassName(), 4711);
        }
        attribute.buildSample(new DateTime(), value).commit();
    }

    /**
     * Renames the object to the title if it differs.
     */
    public static void rename(JEVisObject object, String title) throws JEVisException {
        String name = objectName(title);
        if (!name.equals(object.getName())) {
            object.setName(name);
            object.commit();
        }
    }

    public static String objectName(String title) {
        String name = title == null ? "" : title.trim().replaceAll("[\\r\\n\\t]+", " ");
        if (name.isEmpty()) {
            name = "-";
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    public static JEVisObject create(JEVisObject directory, String className, String title) throws JEVisException {
        JEVisClass jeVisClass = directory.getDataSource().getJEVisClass(className);
        JEVisObject object = directory.buildObject(objectName(title), jeVisClass);
        object.commit();
        return object;
    }

    /**
     * Asks for the directory of a new entry if there are several; shows a hint if there is none.
     *
     * @return the directory, or null if there is none or the user cancelled
     */
    public static JEVisObject chooseDirectory(JEVisDataSource ds, String directoryClassName) {
        List<JEVisObject> directories = new ArrayList<>();
        try {
            JEVisClass directoryClass = ds.getJEVisClass(directoryClassName);
            if (directoryClass != null) {
                for (JEVisObject directory : ds.getObjects(directoryClass, false)) {
                    if (ds.getCurrentUser().canCreate(directory.getID())) {
                        directories.add(directory);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Could not load directories of class {}", directoryClassName, e);
        }
        if (directories.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(JEConfig.getStage());
            TopMenu.applyActiveTheme(alert.getDialogPane().getScene());
            alert.setHeaderText(null);
            alert.setContentText(String.format(I18n.getInstance().getString("plugin.scheduledentries.nodirectory"), directoryClassName));
            alert.showAndWait();
            return null;
        }
        if (directories.size() == 1) {
            return directories.get(0);
        }
        Map<String, JEVisObject> byLabel = new LinkedHashMap<>();
        for (JEVisObject directory : directories) {
            byLabel.put(directoryLabel(directory), directory);
        }
        List<String> labels = new ArrayList<>(byLabel.keySet());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(labels.get(0), labels);
        dialog.initOwner(JEConfig.getStage());
        TopMenu.applyActiveTheme(dialog.getDialogPane().getScene());
        dialog.setTitle(I18n.getInstance().getString("plugin.scheduledentries.directory"));
        dialog.setHeaderText(I18n.getInstance().getString("plugin.scheduledentries.choosedirectory"));
        Optional<String> result = dialog.showAndWait();
        return result.map(byLabel::get).orElse(null);
    }

    /**
     * @return "parent / directory", e.g. the organisation the directory belongs to
     */
    public static String directoryLabel(JEVisObject directory) {
        if (directory == null) {
            return "";
        }
        try {
            List<JEVisObject> parents = directory.getParents();
            if (parents != null && !parents.isEmpty()) {
                return parents.get(0).getName() + " / " + directory.getName();
            }
        } catch (Exception e) {
            logger.debug("Could not read parent of {}", directory, e);
        }
        return directory.getName() + " (" + directory.getID() + ")";
    }

    /**
     * @return the parent directory of an entry, or null
     */
    public static JEVisObject parentOf(JEVisObject entry) {
        try {
            List<JEVisObject> parents = entry.getParents();
            return parents != null && !parents.isEmpty() ? parents.get(0) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String currentUserName(JEVisDataSource ds) {
        try {
            JEVisUser user = ds.getCurrentUser();
            String name = (user.getFirstName() + " " + user.getLastName()).trim();
            return name.isEmpty() ? user.getAccountName() : name;
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean canWrite(JEVisDataSource ds, JEVisObject object) {
        try {
            return ds.getCurrentUser().canWrite(object.getID());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean canDelete(JEVisDataSource ds, JEVisObject object) {
        try {
            return ds.getCurrentUser().canDelete(object.getID());
        } catch (Exception e) {
            return false;
        }
    }

    public static void showError(String message, Exception e) {
        logger.error(message, e);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(JEConfig.getStage());
        TopMenu.applyActiveTheme(alert.getDialogPane().getScene());
        alert.setHeaderText(message);
        alert.setContentText(e != null && e.getMessage() != null ? e.getMessage() : "");
        if (alert.getDialogPane().getScene().getWindow() instanceof Stage) {
            ((Stage) alert.getDialogPane().getScene().getWindow()).setAlwaysOnTop(false);
        }
        alert.showAndWait();
    }

    /**
     * Caches object names for the table (recipients, linked objects).
     */
    public static class NameCache {
        private final JEVisDataSource ds;
        private final Map<Long, String> names = new HashMap<>();

        public NameCache(JEVisDataSource ds) {
            this.ds = ds;
        }

        public String name(Long id) {
            if (id == null) {
                return "";
            }
            return names.computeIfAbsent(id, key -> {
                try {
                    JEVisObject object = ds.getObject(key);
                    return object != null ? object.getName() : "#" + key;
                } catch (Exception e) {
                    return "#" + key;
                }
            });
        }

        public String names(List<Long> ids) {
            StringJoiner joiner = new StringJoiner(", ");
            for (Long id : ids) {
                joiner.add(name(id));
            }
            return joiner.toString();
        }

        public void clear() {
            names.clear();
        }
    }
}
