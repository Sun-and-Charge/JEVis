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

import org.jevis.api.JEVisConstants;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.permission.BuildingRightsTemplate;
import org.jevis.commons.permission.PermissionConsistencyCheck;
import org.jevis.commons.permission.Rights;
import org.jevis.commons.permission.RightsPlan;
import org.jevis.commons.permission.SiteSharing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Translated texts of the permission tools.
 */
final class PermissionTexts {

    private PermissionTexts() {
    }

    static String get(String key, Object... args) {
        return args.length == 0 ? I18n.getInstance().getString(key) : I18n.getInstance().getString(key, args);
    }

    static String findingType(PermissionConsistencyCheck.FindingType type) {
        return get("permission.check.type." + type.name().toLowerCase(Locale.ROOT));
    }

    static String category(RightsPlan.Category category) {
        return get("permission.category." + category.name().toLowerCase(Locale.ROOT));
    }

    static String level(BuildingRightsTemplate.RightsLevel level) {
        return get("permission.template.level." + level.name().toLowerCase(Locale.ROOT));
    }

    static String role(BuildingRightsTemplate.RoleChoice choice) {
        switch (choice) {
            case USER_ROLE:
                return get("permission.template.role.user");
            case ADMIN_ROLE:
                return get("permission.template.role.admin");
            default:
                return get("permission.template.role.skip");
        }
    }

    static String preset(SiteSharing.Preset preset) {
        return preset == null ? "-" : get("permission.preset." + preset.name().toLowerCase(Locale.ROOT));
    }

    static String rights(int mask) {
        String[] keys = {"read", "write", "execute", "create", "delete"};
        List<String> names = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            if ((mask & (1 << i)) != 0) names.add(get("permission.right." + keys[i]));
        }
        return String.join(", ", names);
    }

    static String relationship(int type) {
        if (type == JEVisConstants.ObjectRelationship.OWNER) return get("permission.category.owner");
        if (type == JEVisConstants.ObjectRelationship.ROOT) return get("permission.category.entry_point");
        if (type == JEVisConstants.ObjectRelationship.ROLE_MEMBER) return get("permission.category.role_member");
        if (Rights.isMemberType(type)) return get("permission.rel.member", rights(Rights.bitOfMemberType(type)));
        if (Rights.isRoleRightType(type)) return get("permission.rel.roleright", rights(Rights.bitOfRoleType(type)));
        return String.valueOf(type);
    }

    static String actionKind(RightsPlan.Action action) {
        switch (action.getType()) {
            case CREATE_OBJECT:
                return get("permission.action.create");
            case ADD_RELATIONSHIP:
                return get("permission.action.add");
            default:
                return get("permission.action.remove");
        }
    }

    static String actionWhat(RightsPlan.Action action) {
        if (action.getType() == RightsPlan.ActionType.CREATE_OBJECT) {
            return category(action.getCategory());
        }
        return relationship(action.getRelType());
    }

    static String actionText(RightsPlan.Action action) {
        if (action.getType() == RightsPlan.ActionType.CREATE_OBJECT) {
            return "\"" + action.getObject().getName() + "\" (" + action.getObject().getClassName() + ") ← " + ref(action.getFrom());
        }
        return ref(action.getFrom()) + " → " + ref(action.getTo());
    }

    private static String ref(RightsPlan.Ref ref) {
        return ref.isPlanned() ? ref.getName() + " [" + get("permission.template.new") + "]" : ref.getName() + " [" + ref.getId() + "]";
    }

    static String warning(String warning) {
        if (warning.startsWith("missing class: ")) {
            return get("permission.template.warning.class", warning.substring("missing class: ".length()));
        }
        if (warning.startsWith("missing plugin: ")) {
            return get("permission.template.warning.plugin", warning.substring("missing plugin: ".length()));
        }
        if (warning.startsWith("unknown user: ")) {
            return get("permission.share.warning.user", warning.substring("unknown user: ".length()));
        }
        return warning;
    }

    static String csv(String value) {
        String v = value == null ? "" : value;
        if (v.contains(";") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
