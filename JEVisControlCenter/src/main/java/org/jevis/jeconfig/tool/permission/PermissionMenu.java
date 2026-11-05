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
package org.jevis.jeconfig.tool.permission;

import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.permission.RightsModel;

import java.util.ArrayList;
import java.util.List;

import static org.jevis.jeconfig.tool.permission.PermissionTexts.get;

/**
 * Menu entries of the permission tools. Only Sys Admins get them.
 */
public final class PermissionMenu {

    private static final Logger logger = LogManager.getLogger(PermissionMenu.class);

    private PermissionMenu() {
    }

    private static boolean isSysAdmin(JEVisDataSource ds) {
        try {
            return ds.getCurrentUser().isSysAdmin();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @return context menu entries for an organisation or building (with a leading separator), or an empty list
     */
    public static List<MenuItem> treeItems(JEVisObject object) {
        List<MenuItem> items = new ArrayList<>();
        try {
            String className = object.getJEVisClassName();
            boolean organisation = RightsModel.CLASS_ORGANIZATION.equals(className);
            boolean building = RightsModel.CLASS_BUILDING.equals(className);
            JEVisDataSource ds = object.getDataSource();
            if ((!organisation && !building) || !isSysAdmin(ds)) return items;

            MenuItem template = new MenuItem(get("permission.template.menu"));
            template.setOnAction(e -> new BuildingRightsDialog(ds, object).show());
            MenuItem check = new MenuItem(get("permission.check.menu"));
            check.setOnAction(e -> new PermissionCheckDialog(ds, object).show());
            MenuItem share = new MenuItem(get("permission.share.menu"));
            share.setOnAction(e -> new SiteSharingDialog(ds, object).show());
            items.add(new SeparatorMenuItem());
            items.add(share);
            items.add(template);
            items.add(check);
        } catch (Exception e) {
            logger.warn("Could not build permission menu", e);
        }
        return items;
    }

    /**
     * @return entry "Berechtigungen prüfen …" for the system menu, disabled for users that are not Sys Admin
     */
    public static MenuItem systemItem(JEVisDataSource ds) {
        MenuItem check = new MenuItem(get("permission.check.menu"));
        check.setOnAction(e -> new PermissionCheckDialog(ds, null).show());
        check.setDisable(!isSysAdmin(ds));
        return check;
    }
}
