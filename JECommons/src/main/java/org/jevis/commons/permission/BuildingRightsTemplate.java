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
 * The rights part of the building template: directories, groups, roles, owner and entry point relationships of a
 * building. The same code creates the rights of a new building (Control Center building template) and brings an
 * existing building to this structure ("Rechte nach Vorlage einrichten").
 * <p>
 * The result is a {@link RightsPlan}; nothing is changed here. The plan is idempotent: existing directories, groups
 * and roles are found first (by structure: a group that owns the directory and has an entry point to it; then by
 * name in all template languages) and only missing objects and relationships are planned. Existing groups and
 * relationships are never removed; the only removals are old memberships of migrated users, and only if
 * {@link Options#removeOldMemberships} is set.
 */
public class BuildingRightsTemplate {

    public static final String CLASS_USER_DIRECTORY = "User Directory";
    public static final String CLASS_GROUP_DIRECTORY = "Group Directory";
    public static final String CLASS_ROLE_DIRECTORY = "User Role Directory";
    /**
     * Template languages, in the order of all name arrays: en, de, uk, ru, th, ar.
     */
    static final String[] LANGUAGES = {"en", "de", "uk", "ru", "th", "ar"};
    static final String[] ADMINISTRATION = {"Administration", "Administration", "Адміністрація", "Администрация", "การบริหาร", "إدارة"};
    static final String[] USERS = {"Users", "Benutzer", "користувача", "пользователь", "ผู้ใช้", "المستعمل"};
    static final String[] GROUPS = {"Groups", "Gruppen", "групи", "группы", "กลุ่ม", "مجموعات"};
    static final String[] ROLES = {"Roles", "Benutzerrollen", "ролі користувачів", "роли пользователей", "บทบาทของผู้ใช้", "أدوار المستخدمين"};
    static final String[] USER_ROLE = {"User Role", "Benutzer Rolle", "Роль користувача", "Роль пользователя", "บทบาทของผู้ใช้", "دور المستخدم"};
    static final String[] ADMIN_ROLE = {"Admin Role", "Administrator Rolle", "Роль адміністратора", "Роль администратора", "บทบาทผู้ดูแลระบบ", "دور المسؤول"};
    static final String[] WITH_ENTRY_POINTS = {"Groups With Entry Points", "Gruppen mit Einstiegspunkt", "Групи з точкою входу", "Группы с точкой входа", "กลุ่มที่มีจุดเริ่มต้น", "مجموعات مع نقطة دخول"};
    static final String[] WITHOUT_ENTRY_POINTS = {"Groups Without Entry Points", "Gruppen ohne Einstiegspunkt", "Групи без точки входу", "Группы без точки входа", "กลุ่มที่ไม่มีจุดเริ่มต้น", "مجموعات بدون نقطة دخول"};
    static final String[] PLUGINS = {"Plugins", "Module", "плагіни", "плагины", "ปลั๊กอิน", "الإضافات"};
    static final String[] PLUGIN_SUFFIX = {"Plugin", "Modul", "підключати", "плагин", "เสียบเข้าไป", "توصيل في"};
    /**
     * Directories below the building, each with one group with entry point ("name word X") and optionally one
     * without ("name word").
     */
    public static final List<DataSlot> DATA_SLOTS = Collections.unmodifiableList(Arrays.asList(
            new DataSlot("Alarm Directory", true, "Alarms", "Alarme", "сигналізація", "Аварийная сигнализация", "เตือน", "إنذار"),
            new DataSlot("Action Plan Directory v2", false, "Action Plan", "Aktionsplan", "План дій", "План Действий", "แผนปฏิบัติการ", "خطة عمل"),
            new DataSlot("NonconformityPlan Directory", false, "Non Conformity", "Abweichungen", "Відхилення", "Отклонения", "การเบี่ยงเบน", "الانحرافات"),
            new DataSlot("Index of Legal Provisions Directory", false, "Legal", "Rechtskataster", "Юридичний реєстр", "Юридический реестр", "ทะเบียนทางกฎหมาย", "السجل القانوني"),
            new DataSlot("Analyses Directory", false, "Analyses", "Analysen", "аналізи", "анализы", "การวิเคราะห์", "التحليلات"),
            new DataSlot("Dashboard Directory", false, "Dashboards", "Dashboards", "Приладові панелі", "Панели мониторинга", "แดชบอร์ด", "لوحات المعلومات"),
            new DataSlot("Calculation Directory", false, "Calculations", "Berechnungen", "розрахунки", "расчеты", "การคำนวณ", "العمليات الحسابية"),
            new DataSlot("Report Directory", false, "Reports", "Berichte", "звіти", "отчеты", "รายงาน", "التقارير"),
            new DataSlot("Data Directory", true, "Data", "Daten", "Дані", "Данные", "ข้อมูล", "بيانات"),
            new DataSlot("Data Source Directory", false, "Data Sources", "Datenerfassung", "збір даних", "сбор информации", "การเก็บรวบรวมข้อมูล", "جمع البيانات"),
            new DataSlot("Document Directory", false, "Documents", "Dokumente", "Документи", "Документы", "เอกสาร", "وثائق"),
            new DataSlot("Calendar Directory", false, "Calendar", "Kalender", "календар", "календарь", "ปฏิทิน", "التقويم"),
            new DataSlot("Measurement Directory", false, "Measurement", "Messstellen", "точки вимірювання", "точки измерения", "จุดวัด", "نقاط القياس"),
            new DataSlot("Base Data Directory", true, "Base Data", "Stammdaten", "базові дані", "базовые данные", "ข้อมูลพื้นฐาน", "البيانات الأساسية")
    ));
    /**
     * One group per module, owning the global plugin object.
     */
    public static final List<PluginSlot> PLUGIN_SLOTS = Collections.unmodifiableList(Arrays.asList(
            new PluginSlot("Dashboard Plugin", true, "Dashboard", "Dashboard", "Панель приладів", "Приборная панель", "แผงควบคุม", "لوحة القيادة"),
            new PluginSlot("Graph Plugin", true, "Analyses", "Analysen", "аналізи", "анализы", "การวิเคราะห์", "التحليلات"),
            new PluginSlot("Configuration Plugin", false, "Objects", "Objekte", "об'єктів", "объекты", "วัตถุ", "أشياء"),
            new PluginSlot("Alarm Plugin", true, "Alarms", "Alarme", "сигналізація", "Аварийная сигнализация", "เตือน", "إنذار"),
            new PluginSlot("Report Plugin", true, "Reports", "Berichte", "звіти", "отчеты", "รายงาน", "التقارير"),
            new PluginSlot("Notes Plugin", true, "Notes", "Notizen", "примітки", "примечания", "หมายเหตุ", "ملحوظات"),
            new PluginSlot("Meter Plugin", true, "Measurement", "Messstellen", "точки вимірювання", "точки измерения", "จุดวัด", "نقاط القياس"),
            new PluginSlot("Base Data Plugin", true, "Base Data", "Stammdaten", "базові дані", "базовые данные", "ข้อมูลพื้นฐาน", "البيانات الأساسية"),
            new PluginSlot("Action Plan Plugin", true, "Action Plan", "Aktionsplan", "План дій", "План Действий", "แผนปฏิบัติการ", "خطة عمل"),
            new PluginSlot("Nonconformities Plugin", true, "Non Conformity", "Abweichungen", "Відхилення", "Отклонения", "การเบี่ยงเบน", "الانحرافات"),
            new PluginSlot("Index of Legal Provisions Plugin", true, "Legal", "Rechtskataster", "Юридичний реєстр", "Юридический реестр", "ทะเบียนทางกฎหมาย", "السجل القانوني")
    ));
    private final RightsModel model;
    private final RightsModel.Node building;
    private final String name;

    /**
     * @param model        snapshot of the server
     * @param buildingId   the building
     * @param buildingName name used for new groups, e.g. "Solarpark" gives "Solarpark Data X"
     */
    public BuildingRightsTemplate(RightsModel model, long buildingId, String buildingName) {
        this.model = model;
        this.building = model.get(buildingId);
        if (building == null) throw new IllegalArgumentException("building not in model: " + buildingId);
        this.name = buildingName == null || buildingName.trim().isEmpty() ? building.name : buildingName.trim();
    }

    static Map<String, String> localNames(String[] names) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < LANGUAGES.length && i < names.length; i++) {
            map.put(LANGUAGES[i], names[i]);
        }
        return map;
    }

    static Map<String, String> sameName(String name) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String language : LANGUAGES) map.put(language, name);
        return map;
    }

    public String getBuildingName() {
        return name;
    }

    public RightsModel.Node getBuilding() {
        return building;
    }

    /* ------------------------------------------------------------------ users */

    /**
     * Groups that belong to this building only: groups below the building, and groups elsewhere whose owned objects
     * all lie below the building. Memberships in these groups decide which users are offered for migration.
     */
    public Set<Long> findBuildingLocalGroups() {
        Set<Long> result = new LinkedHashSet<>();
        for (RightsModel.Node group : model.getObjectsOfClass(RightsModel.CLASS_GROUP)) {
            if (model.isInSubtree(group.id, building.id)) {
                result.add(group.id);
                continue;
            }
            boolean ownsAny = false;
            boolean onlyHere = true;
            for (RightsModel.Rel rel : model.getRelationshipsTo(group.id, OWNER)) {
                RightsModel.Node owned = model.get(rel.from);
                if (owned == null || owned.isDeleted()) continue;
                ownsAny = true;
                if (!model.isInSubtree(owned.id, building.id)) {
                    onlyHere = false;
                    break;
                }
            }
            if (ownsAny && onlyHere) result.add(group.id);
        }
        return result;
    }

    /**
     * Users with memberships in groups of this building, with a suggested role: read only → user role; write, create
     * or delete anywhere → admin role (the template knows only these two levels; the admin role keeps the user able
     * to edit). Users already in one of the template roles are suggested to be skipped. Sys Admins are not listed.
     */
    public List<UserCandidate> findUserCandidates() {
        Set<Long> localGroups = findBuildingLocalGroups();
        Structure structure = resolve(new Options());
        List<UserCandidate> result = new ArrayList<>();
        for (RightsModel.Node user : model.getObjectsOfClass(RightsModel.CLASS_USER)) {
            if (user.isSysAdmin()) continue;
            int mask = 0;
            List<Long> groups = new ArrayList<>();
            for (Map.Entry<Long, Integer> entry : Rights.membershipsOf(model, user.id).entrySet()) {
                if (localGroups.contains(entry.getKey())) {
                    mask |= entry.getValue();
                    groups.add(entry.getKey());
                }
            }
            if (groups.isEmpty()) continue;
            RightsLevel level = RightsLevel.of(mask);
            RoleChoice current = RoleChoice.SKIP;
            List<Long> roles = Rights.rolesOf(model, user.id);
            if (structure.adminRoleId != null && roles.contains(structure.adminRoleId)) {
                current = RoleChoice.ADMIN_ROLE;
            } else if (structure.userRoleId != null && roles.contains(structure.userRoleId)) {
                current = RoleChoice.USER_ROLE;
            }
            RoleChoice suggestion = current != RoleChoice.SKIP ? RoleChoice.SKIP
                    : (level == RightsLevel.READ ? RoleChoice.USER_ROLE : RoleChoice.ADMIN_ROLE);
            result.add(new UserCandidate(user, level, mask, groups, current, suggestion));
        }
        result.sort(Comparator.comparing(c -> c.getUserName().toLowerCase()));
        return result;
    }

    /* ------------------------------------------------------------------ structure */

    private RightsModel.Node findChild(long parentId, String className, String[] names, boolean anyIfUnique) {
        List<RightsModel.Node> kids = model.getChildren(parentId, className);
        List<String> nameList = Arrays.asList(names);
        for (RightsModel.Node kid : kids) {
            if (kid.matchesName(nameList)) return kid;
        }
        if (anyIfUnique && kids.size() == 1) return kids.get(0);
        return null;
    }

    private List<RightsModel.Node> groupsBelowBuilding() {
        List<RightsModel.Node> result = new ArrayList<>();
        for (RightsModel.Node node : model.getDescendants(building.id)) {
            if (RightsModel.CLASS_GROUP.equals(node.className)) result.add(node);
        }
        return result;
    }

    private RightsModel.Node findOwnerGroup(List<RightsModel.Node> candidates, long ownedId, Boolean withEntryPoint, Set<Long> used) {
        for (RightsModel.Node group : candidates) {
            if (used.contains(group.id)) continue;
            if (!model.has(ownedId, group.id, OWNER)) continue;
            if (withEntryPoint != null && withEntryPoint != model.has(group.id, ownedId, ROOT)) continue;
            return group;
        }
        return null;
    }

    private RightsModel.Node findByName(List<RightsModel.Node> candidates, Collection<String> names, Set<Long> used) {
        for (RightsModel.Node node : candidates) {
            if (!used.contains(node.id) && node.matchesName(names)) return node;
        }
        return null;
    }

    Structure resolve(Options options) {
        Structure s = new Structure();
        Set<Long> used = new HashSet<>();
        List<RightsModel.Node> groups = groupsBelowBuilding();

        s.administration = findChild(building.id, RightsModel.CLASS_ADMINISTRATION_DIRECTORY, ADMINISTRATION, true);
        if (s.administration != null) {
            s.groupDir = findChild(s.administration.id, CLASS_GROUP_DIRECTORY, GROUPS, true);
            s.roleDir = findChild(s.administration.id, CLASS_ROLE_DIRECTORY, ROLES, true);
            s.userDir = findChild(s.administration.id, CLASS_USER_DIRECTORY, USERS, true);
        }
        if (s.groupDir != null) {
            s.withEntryDir = findChild(s.groupDir.id, CLASS_GROUP_DIRECTORY, WITH_ENTRY_POINTS, false);
            s.withoutEntryDir = findChild(s.groupDir.id, CLASS_GROUP_DIRECTORY, WITHOUT_ENTRY_POINTS, false);
            s.pluginDir = findChild(s.groupDir.id, CLASS_GROUP_DIRECTORY, PLUGINS, false);
        }

        s.buildingGroupX = findOwnerGroup(groups, building.id, true, used);
        if (s.buildingGroupX == null) s.buildingGroupX = findByName(groups, Collections.singletonList(name + " X"), used);
        if (s.buildingGroupX != null) used.add(s.buildingGroupX.id);
        s.buildingGroup = findOwnerGroup(groups, building.id, false, used);
        if (s.buildingGroup == null) s.buildingGroup = findByName(groups, Collections.singletonList(name), used);
        if (s.buildingGroup != null) used.add(s.buildingGroup.id);

        for (DataSlot slot : DATA_SLOTS) {
            SlotState state = new SlotState(slot);
            s.dataSlots.add(state);
            state.classExists = model.hasClass(slot.directoryClass);
            state.directory = findChild(building.id, slot.directoryClass, slot.words, true);
            if (state.directory != null) {
                state.entryGroup = findOwnerGroup(groups, state.directory.id, true, used);
            }
            if (state.entryGroup == null) state.entryGroup = findByName(groups, slot.groupNames(name, true), used);
            if (state.entryGroup != null) used.add(state.entryGroup.id);
            if (options.withNoEntryPointGroups) {
                if (state.directory != null) {
                    state.noEntryGroup = findOwnerGroup(groups, state.directory.id, false, used);
                }
                if (state.noEntryGroup == null) state.noEntryGroup = findByName(groups, slot.groupNames(name, false), used);
                if (state.noEntryGroup != null) used.add(state.noEntryGroup.id);
            }
        }

        for (PluginSlot slot : PLUGIN_SLOTS) {
            PluginState state = new PluginState(slot);
            s.pluginSlots.add(state);
            List<RightsModel.Node> plugins = model.getObjectsOfClass(slot.pluginClass);
            state.plugin = plugins.isEmpty() ? null : plugins.get(0);
            if (state.plugin != null) state.group = findOwnerGroup(groups, state.plugin.id, null, used);
            if (state.group == null) state.group = findByName(groups, slot.groupNames(name), used);
            if (state.group != null) used.add(state.group.id);
        }

        List<RightsModel.Node> roles = new ArrayList<>();
        for (RightsModel.Node node : model.getDescendants(building.id)) {
            if (RightsModel.CLASS_ROLE.equals(node.className)) roles.add(node);
        }
        RightsModel.Node adminRole = findByName(roles, Arrays.asList(ADMIN_ROLE), Collections.emptySet());
        RightsModel.Node userRole = findByName(roles, Arrays.asList(USER_ROLE), Collections.emptySet());
        s.adminRole = adminRole;
        s.userRole = userRole;
        s.adminRoleId = adminRole == null ? null : adminRole.id;
        s.userRoleId = userRole == null ? null : userRole.id;

        if (s.userDir != null) {
            s.serviceUser = findByName(model.getChildren(s.userDir.id, RightsModel.CLASS_USER), Collections.singletonList("JSC@" + name), Collections.emptySet());
        }
        return s;
    }

    /* ------------------------------------------------------------------ plan */

    /**
     * @return the changes that bring the building to the template structure
     */
    public RightsPlan plan(Options options) {
        Planner planner = new Planner(options, resolve(options));
        planner.run();
        return planner.plan;
    }

    public enum RightsLevel {
        READ, WRITE, ADMIN;

        public static RightsLevel of(int mask) {
            if ((mask & (Rights.CREATE | Rights.DELETE)) != 0) return ADMIN;
            if ((mask & Rights.WRITE) != 0) return WRITE;
            return READ;
        }
    }

    public enum RoleChoice {
        SKIP, USER_ROLE, ADMIN_ROLE
    }

    public static class Options {
        /**
         * Also create/ensure the groups without entry point (template option "Create No Entry Points Groups").
         */
        public boolean withNoEntryPointGroups = false;
        /**
         * Create the service user "JSC@name" and make it a member of the admin role (new buildings).
         */
        public boolean createServiceUser = false;
        /**
         * Create template directories (Alarms, Data, …) that the building does not have yet.
         */
        public boolean createMissingDirectories = true;
        /**
         * Give the existing objects below a template directory the same owner groups as the directory, as a new
         * object would get them from its parent.
         */
        public boolean propagateOwnersToSubtree = true;
        /**
         * For migrated users: remove memberships in groups of this building that no template role refers to.
         */
        public boolean removeOldMemberships = false;
        /**
         * User id → role. Users not in the map are not changed.
         */
        public final Map<Long, RoleChoice> userChoices = new LinkedHashMap<>();
    }

    public static class DataSlot {
        public final String directoryClass;
        public final boolean standardExecute;
        final String[] words;

        DataSlot(String directoryClass, boolean standardExecute, String... words) {
            this.directoryClass = directoryClass;
            this.standardExecute = standardExecute;
            this.words = words;
        }

        public String getEnglishName() {
            return words[0];
        }

        String[] groupNameArray(String name, boolean withEntryPoint) {
            String suffix = withEntryPoint ? " X" : "";
            String[] result = new String[LANGUAGES.length];
            for (int i = 0; i < 5; i++) result[i] = name + " " + words[i] + suffix;
            result[5] = words[5] + suffix + " " + name;
            return result;
        }

        List<String> groupNames(String name, boolean withEntryPoint) {
            return Arrays.asList(groupNameArray(name, withEntryPoint));
        }
    }

    public static class PluginSlot {
        public final String pluginClass;
        public final boolean standardRead;
        final String[] words;

        PluginSlot(String pluginClass, boolean standardRead, String... words) {
            this.pluginClass = pluginClass;
            this.standardRead = standardRead;
            this.words = words;
        }

        public String getEnglishName() {
            return words[0];
        }

        String[] groupNameArray(String name) {
            String[] result = new String[LANGUAGES.length];
            for (int i = 0; i < 5; i++) result[i] = name + " " + words[i] + " " + PLUGIN_SUFFIX[i];
            result[5] = PLUGIN_SUFFIX[5] + " " + words[5] + " " + name;
            return result;
        }

        /**
         * @return the template names plus older spellings like "Site Objekte Plugin" or "Site Dashboard Modul"
         */
        List<String> groupNames(String name) {
            List<String> names = new ArrayList<>(Arrays.asList(groupNameArray(name)));
            for (int i = 0; i < 5; i++) {
                names.add(name + " " + words[i] + " Plugin");
                names.add(name + " " + words[i] + " Modul");
            }
            return names;
        }
    }

    static class SlotState {
        final DataSlot slot;
        boolean classExists;
        RightsModel.Node directory;
        RightsModel.Node entryGroup;
        RightsModel.Node noEntryGroup;

        SlotState(DataSlot slot) {
            this.slot = slot;
        }
    }

    static class PluginState {
        final PluginSlot slot;
        RightsModel.Node plugin;
        RightsModel.Node group;

        PluginState(PluginSlot slot) {
            this.slot = slot;
        }
    }

    static class Structure {
        final List<SlotState> dataSlots = new ArrayList<>();
        final List<PluginState> pluginSlots = new ArrayList<>();
        RightsModel.Node administration, groupDir, roleDir, userDir, withEntryDir, withoutEntryDir, pluginDir;
        RightsModel.Node buildingGroup, buildingGroupX, adminRole, userRole, serviceUser;
        Long adminRoleId, userRoleId;
    }

    /**
     * A user that has memberships in groups of the building.
     */
    public static class UserCandidate {
        private final long userId;
        private final String userName;
        private final RightsLevel level;
        private final int rights;
        private final List<Long> groupIds;
        private final RoleChoice currentRole;
        private final RoleChoice suggestion;

        UserCandidate(RightsModel.Node user, RightsLevel level, int rights, List<Long> groupIds, RoleChoice currentRole, RoleChoice suggestion) {
            this.userId = user.id;
            this.userName = user.name;
            this.level = level;
            this.rights = rights;
            this.groupIds = groupIds;
            this.currentRole = currentRole;
            this.suggestion = suggestion;
        }

        public long getUserId() {
            return userId;
        }

        public String getUserName() {
            return userName;
        }

        public RightsLevel getLevel() {
            return level;
        }

        /**
         * @return union of the user's rights in the building's groups (bit mask, see {@link Rights})
         */
        public int getRights() {
            return rights;
        }

        public List<Long> getGroupIds() {
            return groupIds;
        }

        /**
         * @return the template role the user is already in, or SKIP
         */
        public RoleChoice getCurrentRole() {
            return currentRole;
        }

        public RoleChoice getSuggestion() {
            return suggestion;
        }
    }

    /**
     * Builds the plan from the resolved structure.
     */
    private class Planner {
        final RightsPlan plan = new RightsPlan();
        final Options options;
        final Structure s;
        final RightsPlan.Ref buildingRef;
        /**
         * role ref → group ref → planned/existing rights
         */
        final Map<RightsPlan.Ref, Map<RightsPlan.Ref, Integer>> roleRights = new LinkedHashMap<>();
        final Map<RightsPlan.Ref, Set<RightsPlan.Ref>> roleMembers = new LinkedHashMap<>();
        final Map<Long, RightsPlan.Ref> refs = new HashMap<>();
        RightsPlan.Ref administration, groupDir, roleDir, userDir, withEntryDir, withoutEntryDir, pluginDir;

        Planner(Options options, Structure structure) {
            this.options = options;
            this.s = structure;
            this.buildingRef = ref(building);
        }

        RightsPlan.Ref ref(RightsModel.Node node) {
            return refs.computeIfAbsent(node.id, id -> plan.existing(node));
        }

        RightsPlan.Ref ensureObject(RightsModel.Node existing, RightsPlan.Ref parent, String name, String className,
                                    Map<String, String> localNames, RightsPlan.Category category) {
            if (existing != null) return ref(existing);
            return plan.create(parent, name, className, localNames, category);
        }

        void ensureRel(RightsPlan.Ref from, RightsPlan.Ref to, int type, RightsPlan.Category category) {
            if (!from.isPlanned() && !to.isPlanned() && model.has(from.getId(), to.getId(), type)) return;
            plan.addRelationship(from, to, type, category);
        }

        RightsPlan.Ref administration() {
            if (administration == null) {
                administration = ensureObject(s.administration, buildingRef, ADMINISTRATION[0], RightsModel.CLASS_ADMINISTRATION_DIRECTORY,
                        localNames(ADMINISTRATION), RightsPlan.Category.DIRECTORY);
            }
            return administration;
        }

        RightsPlan.Ref groupDir() {
            if (groupDir == null) {
                groupDir = ensureObject(s.groupDir, administration(), GROUPS[0], CLASS_GROUP_DIRECTORY, localNames(GROUPS), RightsPlan.Category.DIRECTORY);
            }
            return groupDir;
        }

        RightsPlan.Ref roleDir() {
            if (roleDir == null) {
                roleDir = ensureObject(s.roleDir, administration(), ROLES[0], CLASS_ROLE_DIRECTORY, localNames(ROLES), RightsPlan.Category.DIRECTORY);
            }
            return roleDir;
        }

        RightsPlan.Ref userDir() {
            if (userDir == null) {
                userDir = ensureObject(s.userDir, administration(), USERS[0], CLASS_USER_DIRECTORY, localNames(USERS), RightsPlan.Category.DIRECTORY);
            }
            return userDir;
        }

        RightsPlan.Ref withEntryDir() {
            if (withEntryDir == null) {
                withEntryDir = ensureObject(s.withEntryDir, groupDir(), WITH_ENTRY_POINTS[0], CLASS_GROUP_DIRECTORY, localNames(WITH_ENTRY_POINTS), RightsPlan.Category.DIRECTORY);
            }
            return withEntryDir;
        }

        RightsPlan.Ref withoutEntryDir() {
            if (withoutEntryDir == null) {
                withoutEntryDir = ensureObject(s.withoutEntryDir, groupDir(), WITHOUT_ENTRY_POINTS[0], CLASS_GROUP_DIRECTORY, localNames(WITHOUT_ENTRY_POINTS), RightsPlan.Category.DIRECTORY);
            }
            return withoutEntryDir;
        }

        RightsPlan.Ref pluginDir() {
            if (pluginDir == null) {
                pluginDir = ensureObject(s.pluginDir, groupDir(), PLUGINS[0], CLASS_GROUP_DIRECTORY, localNames(PLUGINS), RightsPlan.Category.DIRECTORY);
            }
            return pluginDir;
        }

        void grant(RightsPlan.Ref role, RightsPlan.Ref group, int mask) {
            roleRights.computeIfAbsent(role, r -> new LinkedHashMap<>()).merge(group, mask, (a, b) -> a | b);
            for (Integer type : Rights.roleTypes(mask)) {
                ensureRel(role, group, type, RightsPlan.Category.ROLE_RIGHT);
            }
        }

        void run() {
            /* objects first, in the order of the original template */
            administration();
            RightsPlan.Ref serviceUser = null;
            if (options.createServiceUser) {
                String jscName = "JSC@" + name;
                serviceUser = ensureObject(s.serviceUser, userDir(), jscName, RightsModel.CLASS_USER, sameName(jscName), RightsPlan.Category.USER);
            }
            groupDir();
            RightsPlan.Ref userRole = ensureObject(s.userRole, roleDir(), USER_ROLE[0], RightsModel.CLASS_ROLE, localNames(USER_ROLE), RightsPlan.Category.ROLE);
            RightsPlan.Ref adminRole = ensureObject(s.adminRole, roleDir(), ADMIN_ROLE[0], RightsModel.CLASS_ROLE, localNames(ADMIN_ROLE), RightsPlan.Category.ROLE);
            roleRights.put(userRole, new LinkedHashMap<>());
            roleRights.put(adminRole, new LinkedHashMap<>());

            RightsPlan.Ref buildingGroup = ensureObject(s.buildingGroup, groupDir(), name, RightsModel.CLASS_GROUP, sameName(name), RightsPlan.Category.GROUP);
            RightsPlan.Ref buildingGroupX = ensureObject(s.buildingGroupX, groupDir(), name + " X", RightsModel.CLASS_GROUP, sameName(name + " X"), RightsPlan.Category.GROUP);

            List<RightsPlan.Ref[]> slotRefs = new ArrayList<>();
            for (SlotState state : s.dataSlots) {
                if (!state.classExists && state.directory == null) {
                    plan.addWarning("missing class: " + state.slot.directoryClass);
                    slotRefs.add(null);
                    continue;
                }
                if (state.directory == null && !options.createMissingDirectories) {
                    slotRefs.add(null);
                    continue;
                }
                RightsPlan.Ref entryGroup = ensureObject(state.entryGroup, withEntryDir(), state.slot.groupNameArray(name, true)[0],
                        RightsModel.CLASS_GROUP, localNames(state.slot.groupNameArray(name, true)), RightsPlan.Category.GROUP);
                RightsPlan.Ref noEntryGroup = null;
                if (options.withNoEntryPointGroups) {
                    noEntryGroup = ensureObject(state.noEntryGroup, withoutEntryDir(), state.slot.groupNameArray(name, false)[0],
                            RightsModel.CLASS_GROUP, localNames(state.slot.groupNameArray(name, false)), RightsPlan.Category.GROUP);
                }
                RightsPlan.Ref directory = ensureObject(state.directory, buildingRef, state.slot.words[0], state.slot.directoryClass,
                        localNames(state.slot.words), RightsPlan.Category.DIRECTORY);
                slotRefs.add(new RightsPlan.Ref[]{directory, entryGroup, noEntryGroup});
            }

            List<RightsPlan.Ref> pluginGroups = new ArrayList<>();
            for (PluginState state : s.pluginSlots) {
                if (state.plugin == null) {
                    plan.addWarning("missing plugin: " + state.slot.pluginClass);
                    pluginGroups.add(null);
                    continue;
                }
                pluginGroups.add(ensureObject(state.group, pluginDir(), state.slot.groupNameArray(name)[0], RightsModel.CLASS_GROUP,
                        localNames(state.slot.groupNameArray(name)), RightsPlan.Category.GROUP));
            }

            /* owners and entry points */
            ensureRel(buildingRef, buildingGroup, OWNER, RightsPlan.Category.OWNER);
            ensureRel(buildingRef, buildingGroupX, OWNER, RightsPlan.Category.OWNER);
            ensureRel(buildingGroupX, buildingRef, ROOT, RightsPlan.Category.ENTRY_POINT);

            for (int i = 0; i < s.dataSlots.size(); i++) {
                RightsPlan.Ref[] refsOfSlot = slotRefs.get(i);
                if (refsOfSlot == null) continue;
                RightsPlan.Ref directory = refsOfSlot[0];
                ensureRel(directory, refsOfSlot[1], OWNER, RightsPlan.Category.OWNER);
                ensureRel(refsOfSlot[1], directory, ROOT, RightsPlan.Category.ENTRY_POINT);
                if (refsOfSlot[2] != null) ensureRel(directory, refsOfSlot[2], OWNER, RightsPlan.Category.OWNER);
                if (options.propagateOwnersToSubtree && !directory.isPlanned()) {
                    for (RightsModel.Node descendant : model.getDescendants(directory.getId())) {
                        ensureRel(ref(descendant), refsOfSlot[1], OWNER, RightsPlan.Category.OWNER);
                        if (refsOfSlot[2] != null) ensureRel(ref(descendant), refsOfSlot[2], OWNER, RightsPlan.Category.OWNER);
                    }
                }
            }
            for (int i = 0; i < s.pluginSlots.size(); i++) {
                RightsPlan.Ref group = pluginGroups.get(i);
                if (group == null) continue;
                ensureRel(ref(s.pluginSlots.get(i).plugin), group, OWNER, RightsPlan.Category.OWNER);
            }

            /* role rights as in the template */
            for (int i = 0; i < s.dataSlots.size(); i++) {
                RightsPlan.Ref[] refsOfSlot = slotRefs.get(i);
                if (refsOfSlot == null) continue;
                grant(userRole, refsOfSlot[1], Rights.READ | (s.dataSlots.get(i).slot.standardExecute ? Rights.EXECUTE : 0));
                grant(adminRole, refsOfSlot[1], Rights.ALL);
            }
            grant(userRole, buildingGroup, Rights.READ);
            grant(adminRole, buildingGroup, Rights.READ);
            for (int i = 0; i < s.pluginSlots.size(); i++) {
                RightsPlan.Ref group = pluginGroups.get(i);
                if (group == null) continue;
                if (s.pluginSlots.get(i).slot.standardRead) grant(userRole, group, Rights.READ);
                grant(adminRole, group, Rights.READ);
            }
            /* rights the existing roles already had stay part of the role */
            for (RightsPlan.Ref role : Arrays.asList(userRole, adminRole)) {
                if (role.isPlanned()) continue;
                Rights.roleRightsOf(model, role.getId()).forEach((groupId, mask) -> {
                    RightsModel.Node group = model.get(groupId);
                    if (group != null && !group.isDeleted()) {
                        roleRights.get(role).merge(ref(group), mask, (a, b) -> a | b);
                    }
                });
            }

            /* role members */
            for (RightsPlan.Ref role : Arrays.asList(userRole, adminRole)) {
                Set<RightsPlan.Ref> members = new LinkedHashSet<>();
                if (!role.isPlanned()) {
                    for (RightsModel.Rel rel : model.getRelationshipsFrom(role.getId(), ROLE_MEMBER)) {
                        RightsModel.Node user = model.get(rel.to);
                        if (user != null && !user.isDeleted()) members.add(ref(user));
                    }
                }
                roleMembers.put(role, members);
            }
            if (serviceUser != null) {
                ensureRel(adminRole, serviceUser, ROLE_MEMBER, RightsPlan.Category.ROLE_MEMBER);
                roleMembers.get(adminRole).add(serviceUser);
            }
            Set<Long> migrated = new LinkedHashSet<>();
            for (Map.Entry<Long, RoleChoice> choice : options.userChoices.entrySet()) {
                RightsModel.Node user = model.get(choice.getKey());
                if (user == null || choice.getValue() == null || choice.getValue() == RoleChoice.SKIP) continue;
                RightsPlan.Ref role = choice.getValue() == RoleChoice.ADMIN_ROLE ? adminRole : userRole;
                ensureRel(role, ref(user), ROLE_MEMBER, RightsPlan.Category.ROLE_MEMBER);
                roleMembers.get(role).add(ref(user));
                migrated.add(user.id);
            }

            /* materialise the roles: add the missing memberships of all role members (never removes any) */
            for (RightsPlan.Ref role : Arrays.asList(userRole, adminRole)) {
                for (RightsPlan.Ref member : roleMembers.get(role)) {
                    for (Map.Entry<RightsPlan.Ref, Integer> right : roleRights.get(role).entrySet()) {
                        for (Integer type : Rights.memberTypes(right.getValue())) {
                            ensureRel(member, right.getKey(), type, RightsPlan.Category.MEMBERSHIP);
                        }
                    }
                }
            }

            /* optional: remove old memberships of migrated users */
            if (options.removeOldMemberships && !migrated.isEmpty()) {
                Set<Long> roleGroups = new HashSet<>();
                for (Map<RightsPlan.Ref, Integer> rights : roleRights.values()) {
                    for (RightsPlan.Ref group : rights.keySet()) {
                        if (!group.isPlanned()) roleGroups.add(group.getId());
                    }
                }
                Set<Long> localGroups = findBuildingLocalGroups();
                for (Long userId : migrated) {
                    for (RightsModel.Rel rel : model.getRelationshipsFrom(userId)) {
                        if (!Rights.isMemberType(rel.type)) continue;
                        if (!localGroups.contains(rel.to) || roleGroups.contains(rel.to)) continue;
                        plan.removeRelationship(ref(model.get(userId)), ref(model.get(rel.to)), rel.type, RightsPlan.Category.MEMBERSHIP);
                    }
                }
            }
        }
    }
}
