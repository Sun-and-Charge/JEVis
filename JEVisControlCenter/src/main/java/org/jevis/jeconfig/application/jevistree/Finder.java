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
package org.jevis.jeconfig.application.jevistree;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.apache.commons.lang3.StringUtils;
import org.jevis.api.JEVisObject;

import java.util.LinkedHashSet;
import java.util.Set;

public class Finder {

    private final ObservableList<JEVisObject> objects;
    private final JEVisTree tree;
    private JEVisObject selected;
    private final int lastIndex = 0;

    public Finder(JEVisTree jevisTree) {
        /* own list, the visible objects list of the item loader is shared and rebuilt by the tree */
        this.objects = FXCollections.observableArrayList();
        this.tree = jevisTree;
    }

    public void goNext() {
        if (objects.isEmpty()) {
            return;
        }
        if (selected == null) {
            selected = objects.get(0);
        } else {
            int lastIndex = objects.indexOf(selected);
            lastIndex++;

            /** at the end go back to first **/
            if (lastIndex >= objects.size()) {
                lastIndex = 0;
            }

            selected = objects.get(lastIndex);
        }

        showObject(selected);

    }

    public void showObject(JEVisObject obj) {
        Platform.runLater(() -> {
            try {
                JEVisTreeItem item = tree.getItemForObject(obj);
                if (item != null) {
                    tree.getSelectionModel().clearSelection();
                    Platform.runLater(() -> {
                        item.setExpanded(true);
                        tree.openPathToObject(obj);
                        tree.getSelectionModel().select(item);
                        int index = tree.getRow(item) - 5 < 0 ? 0 : tree.getRow(item) - 5;

                        tree.scrollTo(index);
                    });
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    public void goPrevious() {
        if (objects.isEmpty()) {
            return;
        }
        if (selected == null) {
            selected = objects.get(0);
        } else {
            int lastIndex = objects.indexOf(selected);
            lastIndex--;

            /** at the beginning to to end **/
            if (lastIndex < 0) {
                lastIndex = objects.size() - 1;
            }

            selected = objects.get(lastIndex);
        }

        showObject(selected);
    }

    public boolean findMatch(String text) {
        selected = null;

        /* results in the order of the tree, each object once even if name and ID match */
        Set<JEVisObject> result = new LinkedHashSet<>();
        collectMatches(tree.getRoot(), text, result);

        objects.clear();
        if (!result.isEmpty()) {
            objects.setAll(result);
            if (!objects.isEmpty()) {
                goNext();
                return true;
            }

        }

        return false;
    }

    /**
     * Depth-first walk over the tree items in display order.
     */
    private void collectMatches(TreeItem<JEVisTreeRow> item, String text, Set<JEVisObject> result) {
        if (item == null) return;

        if (item instanceof JEVisTreeItem && ((JEVisTreeItem) item).isObject() && item.getValue() != null) {
            JEVisObject object = item.getValue().getJEVisObject();
            if (object != null && (StringUtils.containsIgnoreCase(object.getName(), text)
                    || StringUtils.containsIgnoreCase(String.valueOf(object.getID()), text))) {
                result.add(object);
            }
        }

        for (TreeItem<JEVisTreeRow> child : item.getChildren()) {
            collectMatches(child, text, result);
        }
    }

}
