/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.permission;

import java.util.*;

import static org.jevis.api.JEVisConstants.ObjectRelationship.*;

/**
 * Planning part of the sharing wizard for small sites ("Zugriff einrichten", OpenJEVis #1211, concept step 2).
 * <p>
 * A location (organisation or building) gets at most one group per {@link Preset}: "&lt;location&gt; – Ansehen",
 * "– Bearbeiten" and "– Verwalten" in the Groups directory of the location (Administration / Groups, created if
 * missing). Each group owns the location, has an entry point (ROOT) to it and optionally owns all objects below the
 * location except the administration directories. Every chosen user is made a member of exactly the group of his
 * preset, with the rights of the preset; his memberships in the other two wizard groups are removed. Rights that a
 * role of the user grants on these groups are kept. Nothing else is changed: no other group, membership or role.
 * <p>
 * The result is a {@link RightsPlan}; nothing is changed here. Running the plan twice plans nothing the second time.
 */
public class SiteSharing {

    /**
     * Separators accepted between the location name and the preset word when existing groups are searched.
     */
    static final String[] SEPARATORS = {" – ", " - ", " — "};
    static final String SEPARATOR = " – ";
    private final RightsModel model;
    private final RightsModel.Node location;
    private final String name;
    private final Map<Preset, RightsModel.Node> groups = new EnumMap<>(Preset.class);

    /**
     * @param model        snapshot of the server
     * @param locationId   the organisation or building
     * @param locationName name used for new groups (e.g. the translated name); null uses the object name
     */
    public SiteSharing(RightsModel model, long locationId, String locationName) {
        this.model = model;
        this.location = model.get(locationId);
        if (location == null) throw new IllegalArgumentException("location not in model: " + locationId);
        this.name = locationName == null || locationName.trim().isEmpty() ? location.name : locationName.trim();
        resolveGroups();
    }

    /**
     * @return the name of the wizard group of a preset, e.g. "Solarpark – Ansehen"
     */
    public static String groupName(String locationName, Preset preset) {
        return locationName + SEPARATOR + preset.german;
    }

    static Map<String, String> groupLocalNames(String locationName, Preset preset) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("en", locationName + SEPARATOR + preset.english);
        names.put("de", locationName + SEPARATOR + preset.german);
        return names;
    }

    public RightsModel.Node getLocation() {
        return location;
    }

    public String getLocationName() {
        return name;
    }

    /**
     * @return the existing wizard group of a preset, or null if it does not exist yet
     */
    public RightsModel.Node getGroup(Preset preset) {
        return groups.get(preset);
    }

    /* ------------------------------------------------------------------ groups */

    private void resolveGroups() {
        List<RightsModel.Node> candidates = new ArrayList<>();
        for (RightsModel.Node node : model.getDescendants(location.id)) {
            if (RightsModel.CLASS_GROUP.equals(node.className)) candidates.add(node);
        }
        Set<Long> used = new HashSet<>();
        /* by full name first, then by structure: owns the location and ends with the preset word */
        for (Preset preset : Preset.GROUP_PRESETS) {
            List<String> names = new ArrayList<>();
            for (String separator : SEPARATORS) {
                names.add(name + separator + preset.german);
                names.add(name + separator + preset.english);
                if (!name.equals(location.name)) {
                    names.add(location.name + separator + preset.german);
                    names.add(location.name + separator + preset.english);
                }
            }
            for (RightsModel.Node group : candidates) {
                if (!used.contains(group.id) && group.matchesName(names)) {
                    groups.put(preset, group);
                    used.add(group.id);
                    break;
                }
            }
        }
        for (Preset preset : Preset.GROUP_PRESETS) {
            if (groups.containsKey(preset)) continue;
            for (RightsModel.Node group : candidates) {
                if (used.contains(group.id) || !model.has(location.id, group.id, OWNER)) continue;
                if (endsWithPreset(group, preset)) {
                    groups.put(preset, group);
                    used.add(group.id);
                    break;
                }
            }
        }
    }

    private static boolean endsWithPreset(RightsModel.Node group, Preset preset) {
        List<String> names = new ArrayList<>(group.localNames.values());
        names.add(group.name);
        for (String candidate : names) {
            if (candidate == null) continue;
            String lower = candidate.trim().toLowerCase(Locale.ROOT);
            for (String separator : SEPARATORS) {
                if (lower.endsWith((separator + preset.german).toLowerCase(Locale.ROOT))
                        || lower.endsWith((separator + preset.english).toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------ users */

    /**
     * Users that are members of one of the wizard groups, with the preset of that group. A user in several of the
     * groups gets the highest one (Verwalten before Bearbeiten before Ansehen).
     *
     * @return user id → preset, sorted by user name
     */
    public Map<Long, Preset> currentPresets() {
        Map<Long, Preset> found = new HashMap<>();
        for (Preset preset : Preset.GROUP_PRESETS) {
            RightsModel.Node group = groups.get(preset);
            if (group == null) continue;
            for (RightsModel.Rel rel : model.getRelationshipsTo(group.id)) {
                if (!Rights.isMemberType(rel.type) || rel.from == group.id) continue;
                RightsModel.Node user = model.get(rel.from);
                if (user == null || user.isDeleted() || !RightsModel.CLASS_USER.equals(user.className)) continue;
                found.merge(user.id, preset, (a, b) -> a.ordinal() >= b.ordinal() ? a : b);
            }
        }
        List<Long> ids = new ArrayList<>(found.keySet());
        ids.sort(Comparator.comparing((Long id) -> model.get(id).name.toLowerCase(Locale.ROOT)).thenComparing(id -> id));
        Map<Long, Preset> result = new LinkedHashMap<>();
        for (Long id : ids) result.put(id, found.get(id));
        return result;
    }

    /* ------------------------------------------------------------------ plan */

    /**
     * Objects that the wizard groups own besides the location: everything below it except administration
     * directories (users, groups, roles stay visible to Sys Admins only).
     */
    List<RightsModel.Node> sharedSubtree() {
        List<RightsModel.Node> result = new ArrayList<>();
        Deque<Long> stack = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        seen.add(location.id);
        stack.push(location.id);
        while (!stack.isEmpty()) {
            long current = stack.pop();
            List<RightsModel.Node> kids = model.getChildren(current);
            for (int i = kids.size() - 1; i >= 0; i--) {
                RightsModel.Node kid = kids.get(i);
                if (RightsModel.CLASS_ADMINISTRATION_DIRECTORY.equals(kid.className)) continue;
                if (seen.add(kid.id)) {
                    result.add(kid);
                    stack.push(kid.id);
                }
            }
        }
        return result;
    }

    /**
     * @param assignments     user id → preset. {@link Preset#NONE} removes the user from all wizard groups. Users
     *                        not in the map are not changed.
     * @param includeSubtree  the groups also own the existing objects below the location (new objects get the owners
     *                        of their parent anyway)
     * @return the changes
     */
    public RightsPlan plan(Map<Long, Preset> assignments, boolean includeSubtree) {
        RightsPlan plan = new RightsPlan();
        Map<Long, RightsPlan.Ref> refs = new HashMap<>();
        RightsPlan.Ref locationRef = plan.existing(location);
        refs.put(location.id, locationRef);

        Set<Preset> needed = EnumSet.noneOf(Preset.class);
        for (Preset preset : Preset.GROUP_PRESETS) {
            if (groups.containsKey(preset) || assignments.containsValue(preset)) needed.add(preset);
        }
        if (needed.isEmpty()) return plan;

        BuildingRightsTemplate.Structure structure = new BuildingRightsTemplate(model, location.id, name)
                .resolve(new BuildingRightsTemplate.Options());
        RightsPlan.Ref groupDir = null;
        Map<Preset, RightsPlan.Ref> groupRefs = new EnumMap<>(Preset.class);
        for (Preset preset : Preset.GROUP_PRESETS) {
            if (!needed.contains(preset)) continue;
            RightsModel.Node existing = groups.get(preset);
            if (existing != null) {
                groupRefs.put(preset, refs.computeIfAbsent(existing.id, id -> plan.existing(existing)));
                continue;
            }
            if (groupDir == null) {
                RightsPlan.Ref administration = structure.administration != null ? plan.existing(structure.administration)
                        : plan.create(locationRef, BuildingRightsTemplate.ADMINISTRATION[0], RightsModel.CLASS_ADMINISTRATION_DIRECTORY,
                        BuildingRightsTemplate.localNames(BuildingRightsTemplate.ADMINISTRATION), RightsPlan.Category.DIRECTORY);
                groupDir = structure.groupDir != null ? plan.existing(structure.groupDir)
                        : plan.create(administration, BuildingRightsTemplate.GROUPS[0], BuildingRightsTemplate.CLASS_GROUP_DIRECTORY,
                        BuildingRightsTemplate.localNames(BuildingRightsTemplate.GROUPS), RightsPlan.Category.DIRECTORY);
            }
            groupRefs.put(preset, plan.create(groupDir, groupName(name, preset), RightsModel.CLASS_GROUP,
                    groupLocalNames(name, preset), RightsPlan.Category.GROUP));
        }

        /* owner and entry point */
        List<RightsModel.Node> subtree = includeSubtree ? sharedSubtree() : Collections.emptyList();
        for (RightsPlan.Ref group : groupRefs.values()) {
            ensureRel(plan, locationRef, group, OWNER, RightsPlan.Category.OWNER);
            ensureRel(plan, group, locationRef, ROOT, RightsPlan.Category.ENTRY_POINT);
            for (RightsModel.Node node : subtree) {
                RightsPlan.Ref ref = refs.computeIfAbsent(node.id, id -> plan.existing(node));
                ensureRel(plan, ref, group, OWNER, RightsPlan.Category.OWNER);
            }
        }

        /* memberships */
        for (Map.Entry<Long, Preset> assignment : assignments.entrySet()) {
            RightsModel.Node user = model.get(assignment.getKey());
            Preset chosen = assignment.getValue() == null ? Preset.NONE : assignment.getValue();
            if (user == null || user.isDeleted() || !RightsModel.CLASS_USER.equals(user.className)) {
                plan.addWarning("unknown user: " + assignment.getKey());
                continue;
            }
            RightsPlan.Ref userRef = refs.computeIfAbsent(user.id, id -> plan.existing(user));
            Map<Long, Integer> memberships = Rights.membershipsOf(model, user.id);
            Map<Long, Integer> roleGrants = roleGrants(user.id);
            for (Map.Entry<Preset, RightsPlan.Ref> entry : groupRefs.entrySet()) {
                RightsPlan.Ref group = entry.getValue();
                Long groupId = group.isPlanned() ? null : group.getId();
                int desired = (entry.getKey() == chosen ? chosen.mask : 0)
                        | (groupId == null ? 0 : roleGrants.getOrDefault(groupId, 0));
                int has = groupId == null ? 0 : memberships.getOrDefault(groupId, 0);
                for (Integer type : Rights.memberTypes(desired & ~has)) {
                    plan.addRelationship(userRef, group, type, RightsPlan.Category.MEMBERSHIP);
                }
                for (Integer type : Rights.memberTypes(has & ~desired)) {
                    plan.removeRelationship(userRef, group, type, RightsPlan.Category.MEMBERSHIP);
                }
            }
        }
        return plan;
    }

    /**
     * @return group id → rights that the roles of the user grant (union of all roles)
     */
    private Map<Long, Integer> roleGrants(long userId) {
        Map<Long, Integer> result = new HashMap<>();
        for (Long roleId : Rights.rolesOf(model, userId)) {
            RightsModel.Node role = model.get(roleId);
            if (role == null || role.isDeleted()) continue;
            Rights.roleRightsOf(model, roleId).forEach((group, mask) -> result.merge(group, mask, (a, b) -> a | b));
        }
        return result;
    }

    private void ensureRel(RightsPlan plan, RightsPlan.Ref from, RightsPlan.Ref to, int type, RightsPlan.Category category) {
        if (!from.isPlanned() && !to.isPlanned() && model.has(from.getId(), to.getId(), type)) return;
        plan.addRelationship(from, to, type, category);
    }

    /**
     * The presets of the wizard and of the shortcuts in the membership tab.
     */
    public enum Preset {
        NONE(0, "Keine", "None"),
        VIEW(Rights.READ, "Ansehen", "View"),
        EDIT(Rights.READ | Rights.WRITE | Rights.EXECUTE | Rights.CREATE, "Bearbeiten", "Edit"),
        MANAGE(Rights.ALL, "Verwalten", "Manage");

        /**
         * The presets that have a group, lowest first.
         */
        public static final List<Preset> GROUP_PRESETS = Collections.unmodifiableList(Arrays.asList(VIEW, EDIT, MANAGE));
        public final int mask;
        final String german;
        final String english;

        Preset(int mask, String german, String english) {
            this.mask = mask;
            this.german = german;
            this.english = english;
        }

        /**
         * @return the preset with exactly these rights, or null
         */
        public static Preset ofMask(int mask) {
            for (Preset preset : values()) {
                if (preset.mask == mask) return preset;
            }
            return null;
        }
    }
}
