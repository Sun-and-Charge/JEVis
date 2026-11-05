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
package org.jevis.jeconfig.plugin.object.permission;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.ws.json.*;

import java.util.*;
import java.util.function.Function;

/**
 * Texts and table cells of the effective rights view (OpenJEVis #1211, "Wer darf was?").
 */
public final class EffectiveRightsText {

    public static final String TICK = "✓";

    public static final int READ = 0;
    public static final int WRITE = 1;
    public static final int CREATE = 2;
    public static final int DELETE = 3;
    public static final int EXECUTE = 4;

    private EffectiveRightsText() {
    }

    public static String s(String key) {
        return I18n.getInstance().getString(key);
    }

    public static String s(String key, Object... args) {
        return I18n.getInstance().getString(key, args);
    }

    /**
     * @return the column title of the right
     */
    public static String rightName(int right) {
        switch (right) {
            case READ:
                return s("plugin.object.effectiverights.read");
            case WRITE:
                return s("plugin.object.effectiverights.write");
            case CREATE:
                return s("plugin.object.effectiverights.create");
            case DELETE:
                return s("plugin.object.effectiverights.delete");
            default:
                return s("plugin.object.effectiverights.execute");
        }
    }

    public static String name(String name, long id) {
        return name != null && !name.isEmpty() ? name : "#" + id;
    }

    public static boolean has(JsonEffectiveRights rights, int right) {
        switch (right) {
            case READ:
                return rights.isRead();
            case WRITE:
                return rights.isWrite();
            case CREATE:
                return rights.isCreate();
            case DELETE:
                return rights.isDelete();
            default:
                return rights.isExecute();
        }
    }

    public static boolean has(JsonGroupRights rights, int right) {
        switch (right) {
            case READ:
                return rights.isRead();
            case WRITE:
                return rights.isWrite();
            case CREATE:
                return rights.isCreate();
            case DELETE:
                return rights.isDelete();
            default:
                return rights.isExecute();
        }
    }

    public static List<JsonPermissionSource> sources(JsonEffectiveRights rights, int right) {
        List<JsonPermissionSource> list;
        switch (right) {
            case READ:
                list = rights.getReadSources();
                break;
            case WRITE:
                list = rights.getWriteSources();
                break;
            case CREATE:
                list = rights.getCreateSources();
                break;
            case DELETE:
                list = rights.getDeleteSources();
                break;
            default:
                list = rights.getExecuteSources();
                break;
        }
        return list != null ? list : Collections.emptyList();
    }

    /**
     * @return the user name with the hints Sys Admin / disabled
     */
    public static String userLabel(String userName, long userId, boolean sysAdmin, boolean enabled) {
        StringBuilder sb = new StringBuilder(name(userName, userId));
        if (sysAdmin) sb.append(" (Sys Admin)");
        if (!enabled) sb.append(" (").append(s("plugin.object.effectiverights.disabled")).append(")");
        return sb.toString();
    }

    /**
     * "via group X (via role Y, Z)"
     */
    public static String sourceText(JsonPermissionSource source) {
        StringBuilder sb = new StringBuilder(s("plugin.object.effectiverights.viagroup", name(source.getGroupName(), source.getGroupId())));
        if (source.getRoles() != null && !source.getRoles().isEmpty()) {
            List<String> roles = new ArrayList<>();
            for (JsonPermissionRef role : source.getRoles()) {
                roles.add(s("plugin.object.effectiverights.viarole", name(role.getName(), role.getId())));
            }
            sb.append(" (").append(String.join(", ", roles)).append(")");
        }
        return sb.toString();
    }

    /**
     * @return why the user has (or has not) the right, one line per source
     */
    public static String rightExplanation(JsonEffectiveRights rights, int right) {
        List<String> lines = new ArrayList<>();
        if (rights.isSysAdmin()) {
            lines.add(s("plugin.object.effectiverights.sysadmin"));
        } else {
            if (right == READ && rights.isPublicObject()) lines.add(s("plugin.object.effectiverights.public"));
            if (right == READ && rights.isOwnObject()) lines.add(s("plugin.object.effectiverights.own"));
            for (JsonPermissionSource source : sources(rights, right)) {
                lines.add(sourceText(source));
            }
            if (right == CREATE && rights.getCreateViaExecuteClasses() != null && !rights.getCreateViaExecuteClasses().isEmpty()) {
                lines.add(s("plugin.object.effectiverights.createviaexecute", String.join(", ", rights.getCreateViaExecuteClasses())));
            }
        }
        if (lines.isEmpty()) lines.add(s("plugin.object.effectiverights.norights"));
        return rightName(right) + ":\n" + String.join("\n", lines);
    }

    /**
     * @return a short summary of all sources: one line per group with the rights it grants, plus public / own / Sys Admin
     */
    public static String summary(JsonEffectiveRights rights) {
        if (rights.isSysAdmin()) return s("plugin.object.effectiverights.sysadmin");
        List<String> lines = new ArrayList<>();
        if (rights.isPublicObject()) lines.add(s("plugin.object.effectiverights.public"));
        if (rights.isOwnObject()) lines.add(s("plugin.object.effectiverights.own"));

        Map<Long, JsonPermissionSource> groups = new LinkedHashMap<>();
        Map<Long, List<String>> groupRights = new LinkedHashMap<>();
        Map<Long, Map<Long, String>> groupRoles = new LinkedHashMap<>();
        for (int right = READ; right <= EXECUTE; right++) {
            for (JsonPermissionSource source : sources(rights, right)) {
                groups.putIfAbsent(source.getGroupId(), source);
                groupRights.computeIfAbsent(source.getGroupId(), k -> new ArrayList<>()).add(rightName(right));
                Map<Long, String> roles = groupRoles.computeIfAbsent(source.getGroupId(), k -> new LinkedHashMap<>());
                if (source.getRoles() != null) {
                    for (JsonPermissionRef role : source.getRoles()) roles.put(role.getId(), name(role.getName(), role.getId()));
                }
            }
        }
        for (Map.Entry<Long, JsonPermissionSource> entry : groups.entrySet()) {
            StringBuilder sb = new StringBuilder(s("plugin.object.effectiverights.viagroup",
                    name(entry.getValue().getGroupName(), entry.getKey())));
            sb.append(": ").append(String.join(", ", groupRights.get(entry.getKey())));
            Map<Long, String> roles = groupRoles.get(entry.getKey());
            if (!roles.isEmpty()) {
                List<String> roleTexts = new ArrayList<>();
                for (String role : roles.values()) roleTexts.add(s("plugin.object.effectiverights.viarole", role));
                sb.append(" (").append(String.join(", ", roleTexts)).append(")");
            }
            lines.add(sb.toString());
        }
        if (rights.getCreateViaExecuteClasses() != null && !rights.getCreateViaExecuteClasses().isEmpty()) {
            lines.add(s("plugin.object.effectiverights.createviaexecute", String.join(", ", rights.getCreateViaExecuteClasses())));
        }
        if (lines.isEmpty()) lines.add(s("plugin.object.effectiverights.norights"));
        return String.join("\n", lines);
    }

    /**
     * A centered tick column with an optional tooltip.
     *
     * @param has     whether the row has the right
     * @param tooltip the tooltip text of the row, or {@code null}
     */
    public static <T> TableColumn<T, T> tickColumn(String title, Function<T, Boolean> has, Function<T, String> tooltip) {
        TableColumn<T, T> column = new TableColumn<>(title);
        column.setCellValueFactory(param -> new javafx.beans.property.ReadOnlyObjectWrapper<>(param.getValue()));
        column.setSortable(false);
        column.setPrefWidth(80);
        column.setStyle("-fx-alignment: CENTER;");
        column.setCellFactory(param -> new TableCell<T, T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(Pos.CENTER);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(Boolean.TRUE.equals(has.apply(item)) ? TICK : "");
                if (tooltip != null) {
                    Tooltip tip = new Tooltip(tooltip.apply(item));
                    tip.setWrapText(true);
                    tip.setMaxWidth(600);
                    setTooltip(tip);
                } else {
                    setTooltip(null);
                }
            }
        });
        return column;
    }

    /**
     * A text column with a wrapped, multi-line text.
     */
    public static <T> TableColumn<T, String> textColumn(String title, Function<T, String> text, double prefWidth) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(param -> new javafx.beans.property.ReadOnlyStringWrapper(text.apply(param.getValue())));
        column.setPrefWidth(prefWidth);
        column.setCellFactory(param -> new TableCell<T, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setWrapText(true);
                    setTooltip(new Tooltip(item));
                }
            }
        });
        return column;
    }
}
