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
package org.jevis.jeconfig.application.Chart.ChartPluginElements.tree;

import com.jfoenix.controls.JFXTreeView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.commons.utils.AlphanumComparator;
import org.jevis.jeconfig.application.jevistree.UserSelection;
import org.jevis.jeconfig.application.jevistree.filter.DirectoryHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

public class JEVisTreeView extends JFXTreeView<JEVisTreeViewItem> {
    private final double iconSize = 18d;
    private final AlphanumComparator alphanumComparator = new AlphanumComparator();
    private final boolean showAttributes;
    private final List<JEVisObject> selectedObjects = new ArrayList<>();
    private final List<JEVisAttribute> selectedAttributes = new ArrayList<>();
    private final List<FilterableTreeItem> selectedFilterableTreeItems = new ArrayList<>();
    private final List<FilterableTreeItem> allFilterableTreeItems = new ArrayList<>();
    /**
     * Maps a selected object to the object handed to the caller, e.g. a link to its target (OpenJEVis #1674).
     */
    private UnaryOperator<JEVisObject> selectionResolver = UnaryOperator.identity();

    public JEVisTreeView(JEVisDataSource ds, SelectionMode selectionMode, List<UserSelection> selection, boolean showAttributes) {
        this.showAttributes = showAttributes;
        final FilterableTreeItem rootNode = new FilterableTreeItem(null);
        rootNode.setExpanded(true);
        setShowRoot(false);
        getSelectionModel().setSelectionMode(selectionMode);

        for (UserSelection userSelection : selection) {
            if (userSelection.getType() == UserSelection.SelectionType.Object) {
                selectedObjects.add(userSelection.getSelectedObject());
            } else {
                selectedObjects.add(userSelection.getSelectedAttribute().getObject());
                selectedAttributes.add(userSelection.getSelectedAttribute());
            }
        }

        try {
            List<JEVisObject> rootObjects = ds.getRootObjects();
            rootObjects.sort(objectComparator());

            for (JEVisObject object : rootObjects) {
                try {

                    JEVisTreeViewItem jeVisTreeViewItem = new JEVisTreeViewItem(object);
                    FilterableTreeItem rootItem = new FilterableTreeItem(jeVisTreeViewItem);
                    allFilterableTreeItems.add(rootItem);
                    if (selectedObjects.contains(object)) {
                        selectedFilterableTreeItems.add(rootItem);
                    }

                    addChildren(rootItem);

                    rootNode.getInternalChildren().add(rootItem);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        setCellFactory(ImageTreeCell.callback());

        setRoot(rootNode);

        for (FilterableTreeItem filterableTreeItem : selectedFilterableTreeItems) {
            expandTreeView(filterableTreeItem);
        }

        for (FilterableTreeItem filterableTreeItem : selectedFilterableTreeItems) {
            getSelectionModel().select(filterableTreeItem);
        }

        scrollTo(getSelectionModel().getSelectedIndex());
    }

    private static <T> void expandTreeView(TreeItem<T> selectedItem) {
        if (selectedItem != null) {
            expandTreeView(selectedItem.getParent());
            if (!selectedItem.isLeaf()) {
                selectedItem.setExpanded(true);
            }
        }
    }

    public static TreeItem<JEVisObject> getTreeViewItem(TreeItem<JEVisObject> item, JEVisObject value) {
        if (item != null && item.getValue().equals(value))
            return item;

        for (TreeItem<JEVisObject> child : item.getChildren()) {
            TreeItem<JEVisObject> s = getTreeViewItem(child, value);
            if (s != null)
                return s;
        }
        return null;
    }

    public void select(JEVisObject object) {
        FilterableTreeItem selectedItem = allFilterableTreeItems.stream().filter(filterableTreeItem -> filterableTreeItem.getValue().getObject().equals(object)).findFirst().orElse(null);

        if (selectedItem != null) {
            expandTreeView(selectedItem);
            getSelectionModel().select(selectedItem);
        }
    }

    private void addChildren(FilterableTreeItem rootItem) throws JEVisException {
        if (rootItem.getValue() != null && rootItem.getValue().getItemType() == JEVisTreeViewItem.ItemType.OBJECT) {
            JEVisObject value = rootItem.getValue().getObject();

            List<JEVisObject> children = value.getChildren();
            List<JEVisAttribute> attributes = value.getAttributes();

            children.sort(objectComparator());
            attributes.sort(attributeComparator());

            for (JEVisObject jeVisObject : children) {
                JEVisTreeViewItem jeVisTreeViewItem = new JEVisTreeViewItem(jeVisObject);
                FilterableTreeItem childItem = new FilterableTreeItem(jeVisTreeViewItem);
                rootItem.getInternalChildren().add(childItem);

                allFilterableTreeItems.add(childItem);
                if (selectedObjects.contains(jeVisObject)) {
                    selectedFilterableTreeItems.add(childItem);
                }

                addChildren(childItem);
            }

            if (showAttributes) {
                for (JEVisAttribute attribute : attributes) {

                    JEVisTreeViewItem jeVisTreeViewItem = new JEVisTreeViewItem(attribute);
                    jeVisTreeViewItem.setObject(attribute.getObject());

                    FilterableTreeItem attributeItem = new FilterableTreeItem(jeVisTreeViewItem);
                    rootItem.getInternalChildren().add(attributeItem);

                    allFilterableTreeItems.add(attributeItem);
                    if (selectedAttributes.contains(attribute)) {
                        selectedFilterableTreeItems.add(attributeItem);
                    }
                }
            }
        }
    }

    public List<JEVisObject> getSelectedObjects() {
        List<JEVisObject> result = new ArrayList<>();
        for (TreeItem<JEVisTreeViewItem> selectedItem : getSelectionModel().getSelectedItems()) {
            if (selectedItem instanceof FilterableTreeItem) {
                FilterableTreeItem treeItem = (FilterableTreeItem) selectedItem;
                result.add(selectionResolver.apply(treeItem.getValue().getObject()));
            }
        }

        return result;
    }

    public void setSelectionResolver(UnaryOperator<JEVisObject> selectionResolver) {
        this.selectionResolver = selectionResolver != null ? selectionResolver : UnaryOperator.identity();
    }

    public List<JEVisAttribute> getSelectedAttributes() {
        List<JEVisAttribute> result = new ArrayList<>();
        for (TreeItem<JEVisTreeViewItem> selectedItem : getSelectionModel().getSelectedItems()) {
            if (selectedItem instanceof FilterableTreeItem) {
                FilterableTreeItem treeItem = (FilterableTreeItem) selectedItem;
                result.add(treeItem.getValue().getAttribute());
            }
        }

        return result;
    }

    public List<UserSelection> getUserSelection() {
        List<UserSelection> result = new ArrayList<>();
        for (TreeItem<JEVisTreeViewItem> selectedItem : getSelectionModel().getSelectedItems()) {
            if (selectedItem instanceof FilterableTreeItem) {
                FilterableTreeItem treeItem = (FilterableTreeItem) selectedItem;
                JEVisTreeViewItem treeItemValue = treeItem.getValue();
                UserSelection userSelection;
                if (treeItemValue.getItemType() == JEVisTreeViewItem.ItemType.OBJECT) {
                    userSelection = new UserSelection(UserSelection.SelectionType.Object, selectionResolver.apply(treeItemValue.getObject()));
                } else {
                    userSelection = new UserSelection(UserSelection.SelectionType.Attribute, treeItemValue.getAttribute(), null, null);
                }
                result.add(userSelection);
            }
        }

        return result;
    }

    private Comparator<JEVisObject> objectComparator() {

        return (o1, o2) -> {
            try {
                JEVisDataSource dataSource = o1.getDataSource();
                boolean o1isDir = DirectoryHelper.getInstance(dataSource).getDirectoryNames().contains(o1.getJEVisClassName());
                boolean o2isDir = DirectoryHelper.getInstance(dataSource).getDirectoryNames().contains(o2.getJEVisClassName());

                if (o1isDir && !o2isDir) {
                    return -1;
                } else if (!o1isDir && o2isDir) {
                    return 1;
                }

                return alphanumComparator.compare(o1.getName(), o2.getName());

            } catch (Exception ignored) {
            }

            return 0;
        };
    }

    private Comparator<JEVisAttribute> attributeComparator() {
        return (o1, o2) -> {
            try {
                return Integer.compare(o1.getType().getGUIPosition(), o2.getType().getGUIPosition());
            } catch (Exception ignored) {
            }

            return 0;
        };
    }
}
