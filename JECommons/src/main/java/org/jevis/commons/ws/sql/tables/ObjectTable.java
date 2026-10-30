/**
 * Copyright (C) 2009 - 2016 Envidatec GmbH <info@envidatec.com>
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEAPI-SQL.
 * <p>
 * JEAPI-SQL is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEAPI-SQL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEAPI-SQL. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEAPI-SQL is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.ws.sql.tables;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.api.JEVisException;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonRelationship;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.SQLtoJsonFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC accessor for the {@code object} table.
 * <p>
 * Provides CRUD operations for JEVis objects and helpers for recursive
 * subtree traversal.
 *
 * @author Florian Simon<florian.simon@envidatec.com>
 */
public class ObjectTable {

    /**
     * Name of the database table managed by this class.
     */
    public final static String TABLE = "object";
    /** Column: object ID (primary key). */
    public final static String COLUMN_ID = "id";
    /** Column: display name. */
    public final static String COLUMN_NAME = "name";
    /** Column: JEVis class name. */
    public final static String COLUMN_CLASS = "type";
    /** Column: public-visibility flag. */
    public final static String COLUMN_PUBLIC = "public";
    /** Column: link target (unused in current schema). */
    public final static String COLUMN_LINK = "link";
    /** Column: soft-delete timestamp. */
    public final static String COLUMN_DELETE = "deletets";
    /** Column: user object that deleted the object (added by migration V12). */
    public final static String COLUMN_DELETED_BY = "deletedby";
    /** Column: i18n JSON string. */
    public final static String COLUMN_I18N = "i18n";
    /**
     * Column: group ID.
     */
    public final static String COLUMN_GROUP = "groupid";
    private static final Logger logger = LogManager.getLogger(ObjectTable.class);
    private static volatile Boolean hasDeletedByColumn;
    private final SQLDataSource _connection;

    /**
     * Creates an {@code ObjectTable} accessor backed by the given data source.
     *
     * @param ds the per-request SQL data source
     */
    public ObjectTable(SQLDataSource ds) {
        _connection = ds;
    }

    /**
     * Logs the current character-set configuration of the database connection
     * at DEBUG level. Intended for connection debugging only.
     */
    public void debugConnection() {
        String charVarsSQL = "show variables like 'char%'";

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = _connection.getConnection().prepareStatement(charVarsSQL);

            if (logger.isDebugEnabled()) {
                logger.debug("SQL: {}", ps.toString());
            }

            rs = ps.executeQuery();

            while (rs.next()) {
                logger.error("-- {}: {}", rs.getString(1), rs.getString(2));
            }

        } catch (Exception ex) {
            logger.error(ex);
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException ex) {
                    logger.debug("Error while closing DB connection: {}. ", ex);
                }
            }
        }
    }

    /**
     * Inserts a new object into the database, creates a PARENT relationship to
     * the given parent, and copies all OWNER relationships from the parent.
     *
     * @param name     the display name for the new object
     * @param jclass   the JEVis class name
     * @param parent   the ID of the parent object
     * @param isPublic whether the object is publicly visible
     * @param i18n     i18n JSON string, may be {@code null}
     * @return the newly created {@link JsonObject}
     * @throws JEVisException if the insert or relationship creation fails
     */
    public JsonObject insertObject(String name, String jclass, long parent, boolean isPublic, String i18n) throws JEVisException {
        String sql = String.format("insert into %s(%s, %s, %s, %s) values(?,?,?,?)", TABLE, COLUMN_NAME, COLUMN_CLASS, COLUMN_PUBLIC, COLUMN_I18N);


        try (PreparedStatement ps = _connection.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, jclass);
            ps.setBoolean(3, isPublic);
            ps.setString(4, i18n);

            if (logger.isDebugEnabled()) {
                logger.debug("SQL: {}", ps);
            }
            int count = ps.executeUpdate();
            if (count == 1) {
                ResultSet rs = ps.getGeneratedKeys();

                if (rs.next()) {

                    for (JsonRelationship rel : _connection.getRelationshipTable().selectForObject(parent)) {

                        if (rel.getType() == JEVisConstants.ObjectRelationship.OWNER && rel.getFrom() == parent) {
                            _connection.getRelationshipTable().insert(rs.getLong(1), rel.getTo(), JEVisConstants.ObjectRelationship.OWNER);
                        }
                    }

                    int relType = JEVisConstants.ObjectRelationship.PARENT;//not very save
                    _connection.getRelationshipTable().insert(rs.getLong(1), parent, relType);

                    return getObject(rs.getLong(1));
                } else {
                    throw new JEVisException("Error selecting inserted object", 234235);
                }
            } else {
                throw new JEVisException("Error while inserting object", 234236);
            }
        } catch (SQLException ex) {
            logger.error(ex);
            throw new JEVisException("Error while inserting object", 234237);
        }


    }

    /**
     * Updates the name, public flag, and i18n metadata of an existing object.
     *
     * @param id       the object ID to update
     * @param newname  the new display name
     * @param ispublic the new public-visibility flag
     * @param i18n     the new i18n JSON string
     * @return the updated {@link JsonObject}
     * @throws JEVisException if the update fails
     */
    public JsonObject updateObject(long id, String newname, boolean ispublic, String i18n) throws JEVisException {
        String sql = String.format("update %s set %s=?,%s=?,%s=? where %s=?", TABLE, COLUMN_NAME, COLUMN_PUBLIC, COLUMN_I18N, COLUMN_ID);
        try (PreparedStatement ps = _connection.getConnection().prepareStatement(sql)) {
            ps.setString(1, newname);
            ps.setBoolean(2, ispublic);
            ps.setString(3, i18n);
            ps.setLong(4, id);

            if (logger.isDebugEnabled()) {
                logger.debug("SQL: {}", ps);
            }
            int count = ps.executeUpdate();
            if (count == 1) {
                return getObject(id);
            } else {
                throw new JEVisException("Error while updating object", 234236);//ToDo real number
            }
        } catch (SQLException ex) {
            logger.error(ex);
            throw new JEVisException("Error while updating object", 234234, ex);//ToDo real number
        }


    }

    /**
     * Retrieves a single object by ID from the database and resolves its parent
     * relationship.
     *
     * @param id the object ID
     * @return the matching {@link JsonObject}, or {@code null} if not found
     * @throws JEVisException if a database error occurs
     */
    public JsonObject getObject(Long id) throws JEVisException {
        logger.trace("getObject: {} ", id);

        String sql = String.format("select o.* from %s o where  o.%s=? limit 1 ", TABLE, COLUMN_ID);

        try (PreparedStatement ps = _connection.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);

            logger.trace("getObject.sql: {} ", ps);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                JsonObject obj = SQLtoJsonFactory.buildObject(rs);
                List<JsonRelationship> relationships = _connection.getParentRelationships(obj.getId());
                relationships.forEach(jsonRelationship -> {
                    if (jsonRelationship.getType() == JEVisConstants.ObjectRelationship.PARENT && jsonRelationship.getFrom() == obj.getId()) {
                        //child -> parent
                        obj.setParent(jsonRelationship.getTo());
                    }
                });

                return obj;
            }
        } catch (SQLException ex) {
            logger.error(ex);

        }
        return null;
    }


    /**
     * Returns all public objects in the database.
     *
     * @return a list of publicly-visible {@link JsonObject} instances
     * @throws JEVisException if the query fails
     */
    public List<JsonObject> getAllPublicObjects() throws JEVisException {
        logger.trace("getPublicObjects");

        String sql = String.format("select * from %s where %s=1", TABLE, COLUMN_PUBLIC);


        List<JsonObject> objects = new ArrayList<>();

        try (PreparedStatement ps = _connection.getConnection().prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try {
                    objects.add(SQLtoJsonFactory.buildObject(rs));
                } catch (Exception ex) {
                    logger.error("Cound not load Object: " + ex.getMessage());
                }
            }
        } catch (SQLException ex) {
            logger.error(ex);
        }

        return objects;
    }

    /**
     * Returns all objects in the database (including soft-deleted ones).
     *
     * @return a list of all {@link JsonObject} instances
     * @throws JEVisException if the query fails
     */
    public List<JsonObject> getAllObjects() throws JEVisException {

        String sql = String.format("select * from %s", TABLE);

        List<JsonObject> objects = new ArrayList<>();

        try (PreparedStatement ps = _connection.getConnection().prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try {
                    objects.add(SQLtoJsonFactory.buildObject(rs));
                } catch (Exception ex) {
                    logger.error("Could not load Object: " + ex.getMessage());
                }
            }
        } catch (SQLException ex) {
            logger.error(ex);
        }

        return objects;
    }

    /**
     * Returns all objects that have been soft-deleted (i.e., have a non-null
     * {@code deletets} column).
     *
     * @return a list of deleted {@link JsonObject} instances
     * @throws JEVisException if the query fails
     */
    public List<JsonObject> getAllDeletedObjects() throws JEVisException {

        String sql = String.format("select * from %s where %s is not null", TABLE, COLUMN_DELETE);

        List<JsonObject> objects = new ArrayList<>();

        try (PreparedStatement ps = _connection.getConnection().prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try {
                    objects.add(SQLtoJsonFactory.buildObject(rs));
                } catch (Exception ex) {
                    logger.error("Could not load Object: " + ex.getMessage());
                }
            }
        } catch (SQLException ex) {
            logger.error(ex);
        }

        return objects;
    }


    /**
     * Returns all objects whose JEVis class is {@code "Group"}, keyed by ID.
     *
     * @return a map from object ID to {@link JsonObject} for all group objects
     * @throws JEVisException if the query fails
     */
    public Map<Long, JsonObject> getGroupObjects() throws JEVisException {

        String sql = String.format("select * from %s where %s is null and %s=?", TABLE, COLUMN_DELETE, COLUMN_CLASS);

        Map<Long, JsonObject> objects = new ConcurrentHashMap<>();

        try (PreparedStatement ps = _connection.getConnection().prepareStatement(sql)) {
            ps.setString(1, "Group");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try {
                    JsonObject object = SQLtoJsonFactory.buildObject(rs);
                    objects.put(object.getId(), object);
                } catch (Exception ex) {
                    logger.error("Could not load UserGroup: {}", ex.getMessage());
                }
            }
        } catch (SQLException ex) {
            logger.error(ex);
        }

        return objects;
    }

    /**
     * Relationship lookups for one delete, restore or purge, read fresh from the database (the request cache may be stale).
     */
    private static class Tree {
        final Map<Long, List<Long>> childrenByParent = new HashMap<>();
        final Map<Long, List<Long>> parentsByChild = new HashMap<>();
        final Map<Long, List<Long>> deletedChildrenByParent = new HashMap<>();
        final Set<Long> deletedIds = new HashSet<>();
    }

    private Tree loadTree() throws SQLException {
        Tree tree = new Tree();
        String relSql = String.format("select %s, %s, %s from %s where %s in (?, ?)", RelationshipTable.COLUMN_START,
                RelationshipTable.COLUMN_END, RelationshipTable.COLUMN_TYPE, RelationshipTable.TABLE, RelationshipTable.COLUMN_TYPE);
        try (PreparedStatement ps = _connection.getConnection().prepareStatement(relSql)) {
            ps.setInt(1, JEVisConstants.ObjectRelationship.PARENT);
            ps.setInt(2, JEVisConstants.ObjectRelationship.DELETED_PARENT);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long child = rs.getLong(1);
                long parent = rs.getLong(2);
                if (rs.getInt(3) == JEVisConstants.ObjectRelationship.PARENT) {
                    tree.childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
                    tree.parentsByChild.computeIfAbsent(child, k -> new ArrayList<>()).add(parent);
                } else {
                    tree.deletedChildrenByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
                }
            }
        }
        try (PreparedStatement ps = _connection.getConnection().prepareStatement(
                String.format("select %s from %s where %s is not null", COLUMN_ID, TABLE, COLUMN_DELETE))) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                tree.deletedIds.add(rs.getLong(1));
            }
        }
        return tree;
    }

    /**
     * Objects reachable from {@code rootId} along PARENT relationships (and DELETED_PARENT ones if
     * {@code followDeletedParents}), without the nodes in {@code excluded} and their descendants. With
     * {@code onlyDeleted} only objects in the recycle bin are followed.
     */
    private Set<Long> reach(Tree tree, long rootId, boolean followDeletedParents, boolean onlyDeleted, Set<Long> excluded) {
        Set<Long> visited = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        visited.add(rootId);
        queue.add(rootId);
        while (!queue.isEmpty()) {
            long node = queue.poll();
            List<Long> children = new ArrayList<>(tree.childrenByParent.getOrDefault(node, Collections.emptyList()));
            if (followDeletedParents) {
                children.addAll(tree.deletedChildrenByParent.getOrDefault(node, Collections.emptyList()));
            }
            for (long child : children) {
                if (excluded.contains(child) || (onlyDeleted && !tree.deletedIds.contains(child))) {
                    continue;
                }
                if (visited.add(child)) {
                    queue.add(child);
                }
            }
        }
        return visited;
    }

    /**
     * The subtree that a delete or purge of {@code rootId} affects. Children that also hang below a live parent outside the
     * subtree stay out, they are still visible there.
     */
    private Set<Long> subtreeForDelete(Tree tree, long rootId, boolean followDeletedParents) {
        Set<Long> excluded = new HashSet<>();
        Set<Long> subtree = reach(tree, rootId, followDeletedParents, false, excluded);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (long id : subtree) {
                if (id == rootId || excluded.contains(id)) {
                    continue;
                }
                for (long parent : tree.parentsByChild.getOrDefault(id, Collections.emptyList())) {
                    if (!subtree.contains(parent) && !tree.deletedIds.contains(parent)) {
                        excluded.add(id);
                        changed = true;
                        break;
                    }
                }
            }
            if (changed) {
                subtree = reach(tree, rootId, followDeletedParents, false, excluded);
            }
        }
        return subtree;
    }

    private static String inClause(Collection<Long> ids) {
        StringBuilder in = new StringBuilder("(");
        for (int i = 0; i < ids.size(); i++) {
            in.append(i == 0 ? "?" : ",?");
        }
        return in.append(")").toString();
    }

    private static int setIds(PreparedStatement ps, int index, Collection<Long> ids) throws SQLException {
        for (long id : ids) {
            ps.setLong(index++, id);
        }
        return index;
    }

    private interface SqlWork<T> {
        T run(Connection con) throws SQLException, JEVisException;
    }

    /**
     * Runs the work in one transaction on the connection of this request.
     */
    private <T> T inTransaction(SqlWork<T> work) throws JEVisException {
        try {
            Connection con = _connection.getConnection();
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                T result = work.run(con);
                con.commit();
                return result;
            } catch (SQLException | JEVisException | RuntimeException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            logger.error(ex, ex);
            throw new JEVisException("Database error: " + ex.getMessage(), 5439, ex);
        }
    }

    private boolean hasDeletedByColumn(Connection con) throws SQLException {
        if (hasDeletedByColumn == null) {
            try (ResultSet rs = con.getMetaData().getColumns(con.getCatalog(), null, TABLE, COLUMN_DELETED_BY)) {
                hasDeletedByColumn = rs.next();
            }
        }
        return hasDeletedByColumn;
    }

    /**
     * Moves the object and its subtree to the recycle bin: sets {@code deletets} (and {@code deletedby}) and turns the
     * object's PARENT relationships into DELETED_PARENT, so the bin knows where it came from.
     *
     * @param obj       the object to delete
     * @param deletedBy ID of the user object that deletes it, may be {@code null}
     * @return the IDs of the objects moved to the bin
     */
    public Set<Long> markObjectAsDeleted(JsonObject obj, Long deletedBy) throws JEVisException {
        return inTransaction(con -> {
            Set<Long> ids = subtreeForDelete(loadTree(), obj.getId(), false);
            boolean withUser = hasDeletedByColumn(con);
            String sql = String.format("update %s set %s=?%s where %s in %s", TABLE, COLUMN_DELETE,
                    withUser ? ", " + COLUMN_DELETED_BY + "=?" : "", COLUMN_ID, inClause(ids));
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int index = 1;
                ps.setTimestamp(index++, new Timestamp(System.currentTimeMillis()));
                if (withUser) {
                    if (deletedBy != null) {
                        ps.setLong(index++, deletedBy);
                    } else {
                        ps.setNull(index++, Types.BIGINT);
                    }
                }
                setIds(ps, index, ids);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(String.format("update %s set %s=? where %s=? and %s=?",
                    RelationshipTable.TABLE, RelationshipTable.COLUMN_TYPE, RelationshipTable.COLUMN_START, RelationshipTable.COLUMN_TYPE))) {
                ps.setInt(1, JEVisConstants.ObjectRelationship.DELETED_PARENT);
                ps.setLong(2, obj.getId());
                ps.setInt(3, JEVisConstants.ObjectRelationship.PARENT);
                ps.executeUpdate();
            }
            return ids;
        });
    }

    /**
     * Restores the object and the subtree that was deleted with it.
     * <p>
     * Without {@code targetParent} the object goes back below its original parents that are not deleted. With
     * {@code targetParent} it is placed below that object instead. If {@code requireParent} is set and the object would end
     * up without a live parent, nothing is changed and an exception is thrown; otherwise only the deletion mark is removed
     * (older clients set the new parent themselves after the restore).
     *
     * @return the IDs of the restored objects
     */
    public Set<Long> restoreObject(JsonObject obj, Long targetParent, boolean requireParent) throws JEVisException {
        return inTransaction(con -> {
            Tree tree = loadTree();
            if (!tree.deletedIds.contains(obj.getId())) {
                throw new JEVisException("Object is not in the recycle bin", 409);
            }
            List<Long> oldParents = new ArrayList<>();
            tree.deletedChildrenByParent.forEach((parent, children) -> {
                if (children.contains(obj.getId())) {
                    oldParents.add(parent);
                }
            });

            List<Long> newParents = new ArrayList<>();
            if (targetParent != null) {
                if (tree.deletedIds.contains(targetParent) || getObject(targetParent) == null) {
                    throw new JEVisException("Target parent does not exist or is in the recycle bin", 409);
                }
                newParents.add(targetParent);
            } else {
                for (long parent : oldParents) {
                    if (!tree.deletedIds.contains(parent) && getObject(parent) != null) {
                        newParents.add(parent);
                    }
                }
            }
            boolean hasLiveParent = tree.parentsByChild.getOrDefault(obj.getId(), Collections.emptyList()).stream()
                    .anyMatch(parent -> !tree.deletedIds.contains(parent));
            if (requireParent && newParents.isEmpty() && !hasLiveParent) {
                throw new JEVisException("The original parent no longer exists, choose a target", 409);
            }

            Set<Long> ids = reach(tree, obj.getId(), false, true, Collections.emptySet());
            boolean withUser = hasDeletedByColumn(con);
            String sql = String.format("update %s set %s=null%s where %s in %s", TABLE, COLUMN_DELETE,
                    withUser ? ", " + COLUMN_DELETED_BY + "=null" : "", COLUMN_ID, inClause(ids));
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                setIds(ps, 1, ids);
                ps.executeUpdate();
            }

            if (targetParent != null) {
                // taken out of a deleted folder: it no longer belongs there
                try (PreparedStatement ps = con.prepareStatement(String.format(
                        "delete from %s where %s=? and %s=? and %s in (select %s from %s where %s is not null)",
                        RelationshipTable.TABLE, RelationshipTable.COLUMN_START, RelationshipTable.COLUMN_TYPE,
                        RelationshipTable.COLUMN_END, COLUMN_ID, TABLE, COLUMN_DELETE))) {
                    ps.setLong(1, obj.getId());
                    ps.setInt(2, JEVisConstants.ObjectRelationship.PARENT);
                    ps.executeUpdate();
                }
            }
            if (!newParents.isEmpty()) {
                try (PreparedStatement ps = con.prepareStatement(String.format("delete from %s where %s=? and %s=?",
                        RelationshipTable.TABLE, RelationshipTable.COLUMN_START, RelationshipTable.COLUMN_TYPE))) {
                    ps.setLong(1, obj.getId());
                    ps.setInt(2, JEVisConstants.ObjectRelationship.DELETED_PARENT);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(String.format("insert ignore into %s (%s, %s, %s) values (?, ?, ?)",
                        RelationshipTable.TABLE, RelationshipTable.COLUMN_START, RelationshipTable.COLUMN_END, RelationshipTable.COLUMN_TYPE))) {
                    for (long parent : newParents) {
                        ps.setLong(1, obj.getId());
                        ps.setLong(2, parent);
                        ps.setInt(3, JEVisConstants.ObjectRelationship.PARENT);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            return ids;
        });
    }

    /**
     * Permanently deletes the object and its subtree, including children that were moved to the recycle bin on their own,
     * with their samples, attribute rows, relationships and stored files.
     *
     * @return the IDs of the deleted objects
     */
    public Set<Long> deleteObjectFromDB(JsonObject obj) throws JEVisException {
        Set<Long> ids = inTransaction(con -> {
            Set<Long> subtree = subtreeForDelete(loadTree(), obj.getId(), true);
            String in = inClause(subtree);
            String[] tables = {SampleTable.TABLE, AttributeTable.TABLE};
            for (String table : tables) {
                try (PreparedStatement ps = con.prepareStatement(String.format("delete from %s where object in %s", table, in))) {
                    setIds(ps, 1, subtree);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = con.prepareStatement(String.format("delete from %s where %s in %s or %s in %s",
                    RelationshipTable.TABLE, RelationshipTable.COLUMN_START, in, RelationshipTable.COLUMN_END, in))) {
                setIds(ps, setIds(ps, 1, subtree), subtree);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(String.format("delete from %s where %s in %s", TABLE, COLUMN_ID, in))) {
                setIds(ps, 1, subtree);
                ps.executeUpdate();
            }
            return subtree;
        });

        for (long id : ids) {
            deleteFiles(new File(Config.getFileDir(), String.valueOf(id)));
        }
        return ids;
    }

    private static void deleteFiles(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(dir.toPath())) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ex) {
                    logger.warn("Could not delete {}: {}", path, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            logger.warn("Could not delete files of {}: {}", dir, ex.getMessage());
        }
    }

    /**
     * Objects in the recycle bin whose parent is not deleted (or that have no parent left): the entries shown at the top
     * level of the bin. Everything else in the bin belongs to their subtrees.
     *
     * @return IDs of the top-level bin objects
     */
    public Set<Long> getRecycleBinRoots() throws JEVisException {
        try {
            Tree tree = loadTree();
            Set<Long> roots = new LinkedHashSet<>();
            for (long id : tree.deletedIds) {
                boolean deletedParent = tree.parentsByChild.getOrDefault(id, Collections.emptyList()).stream()
                        .anyMatch(tree.deletedIds::contains);
                if (!deletedParent) {
                    roots.add(id);
                }
            }
            return roots;
        } catch (SQLException ex) {
            logger.error(ex, ex);
            throw new JEVisException("Database error: " + ex.getMessage(), 5439, ex);
        }
    }

}
