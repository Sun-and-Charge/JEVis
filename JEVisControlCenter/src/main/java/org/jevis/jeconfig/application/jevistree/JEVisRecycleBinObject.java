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

/**
 * Copyright (C) 2009 - 2014 Envidatec GmbH <info@envidatec.com>
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.i18n.I18n;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.util.*;

/**
 * Virtual root of the recycle bin in the tree. Its children are the deleted objects whose parent is not deleted; the
 * objects deleted together with them hang below them as usual.
 *
 * @author fs
 */
public class JEVisRecycleBinObject implements JEVisObject {

    private static final Logger logger = LogManager.getLogger(JEVisRecycleBinObject.class);
    final JEVisDataSource ds;
    private String _name = I18n.getInstance().getString("plugin.object.recyclebin.name");
    public static String CLASS_NAME = "Recycle Bin";

    public JEVisRecycleBinObject(JEVisDataSource ds) {
        this.ds = ds;
    }

    @Override
    public List<JEVisObject> getParents() throws JEVisException {
        return new ArrayList<>();
    }

    @Override
    public JEVisObject getParent() throws JEVisException {
        return null;
    }

    /**
     * Whether the object is a top-level entry of the recycle bin: it is deleted and none of its parents is.
     */
    public static boolean isRecycleBinRoot(JEVisObject object) {
        try {
            if (object == null || object.getDeleteTS() == null) {
                return false;
            }
            for (JEVisObject parent : object.getParents()) {
                if (parent.getDeleteTS() != null) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            logger.error("Could not check recycle bin state of {}", object, ex);
            return false;
        }
    }

    /**
     * Text for the tooltip of an object in the bin: when and by whom it was deleted and, for a top-level entry, where it
     * was before.
     */
    public static String describeDeletion(JEVisObject object) {
        if (object == null || object.getDeleteTS() == null) {
            return null;
        }
        String time = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm").print(object.getDeleteTS());
        StringBuilder text = new StringBuilder();
        if (object.getDeletedBy() != null) {
            text.append(I18n.getInstance().getString("jevistree.recyclebin.info", time, object.getDeletedBy()));
        } else {
            text.append(I18n.getInstance().getString("jevistree.recyclebin.info.nouser", time));
        }
        try {
            for (JEVisRelationship rel : object.getRelationships(JEVisConstants.ObjectRelationship.DELETED_PARENT, JEVisConstants.Direction.FORWARD)) {
                JEVisObject parent = rel.getEndObject();
                if (parent != null) {
                    text.append(System.lineSeparator()).append(I18n.getInstance().getString("jevistree.recyclebin.origin", pathOf(parent)));
                }
            }
        } catch (Exception ex) {
            logger.debug("No original location for {}", object, ex);
        }
        return text.toString();
    }

    private static String pathOf(JEVisObject object) throws JEVisException {
        Deque<String> names = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        JEVisObject current = object;
        while (current != null && visited.add(current.getID())) {
            names.addFirst(current.getName());
            List<JEVisObject> parents = current.getParents();
            current = parents.isEmpty() ? null : parents.get(0);
        }
        return String.join(" / ", names);
    }

    @Override
    public List<JEVisObject> getChildren() throws JEVisException {
        List<JEVisObject> children = new ArrayList<>();
        for (JEVisObject deleted : ds.getDeletedObjects()) {
            if (isRecycleBinRoot(deleted)) {
                children.add(deleted);
            }
        }
        return children;
    }

    @Override
    public List<JEVisObject> getChildren(JEVisClass type, boolean inherit) throws JEVisException {
        return new ArrayList<>();
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public void setName(String name) {
        _name = name;
    }

    @Override
    public String getLocalName(String key) {
        return getName();
    }

    @Override
    public void setLocalName(String key, String name) {

    }

    @Override
    public void setLocalNames(Map<String, String> translation) {

    }

    @Override
    public Map<String, String> getLocalNameList() {
        return new HashMap<>();
    }

    @Override
    public Long getID() {
        return -1L;
    }

    @Override
    public JEVisClass getJEVisClass() {
        try {
            return ds.getJEVisClass(CLASS_NAME);
        } catch (JEVisException e) {
            e.printStackTrace();
        }
        return null;
    }


    @Override
    public List<JEVisAttribute> getAttributes() {
        return new ArrayList<>();
    }

    @Override
    public JEVisAttribute getAttribute(JEVisType type) {
        return null;
    }

    @Override
    public JEVisAttribute getAttribute(String type) {
        return null;
    }

    @Override
    public boolean isAllowedUnder(JEVisObject otherObject) {
        return false;
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public JEVisObject buildObject(String name, JEVisClass type) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public JEVisObject getLinkedObject() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public JEVisRelationship buildRelationship(JEVisObject obj, int type, int direction) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deleteRelationship(JEVisRelationship rel) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<JEVisRelationship> getRelationships() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<JEVisRelationship> getRelationships(int type) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<JEVisRelationship> getRelationships(int type, int direction) {
        return new ArrayList<>();
    }

    @Override
    public List<JEVisClass> getAllowedChildrenClasses() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public JEVisDataSource getDataSource() {
        return null;
    }

    @Override
    public void commit() throws JEVisException {
    }

    @Override
    public void rollBack() {
    }

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public int compareTo(JEVisObject o) {
        return -1;
    }

    @Override
    public String toString() {
        return "JEVisRootObject{ This Object is a fake and does not exist on the Server }";
    }

    //    @Override
//    public JEVisObject buildLink(String name, JEVisObject parent) throws JEVisException {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//    }
    @Override
    public String getJEVisClassName() {
        return "Recycle Bin";
    }

    @Override
    public boolean isPublic() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setIsPublic(boolean ispublic) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addEventListener(JEVisEventListener listener) {

    }

    @Override
    public void removeEventListener(JEVisEventListener listener) {

    }

    @Override
    public JEVisEventListener[] getEventListener() {
        return null;
    }

    @Override
    public void notifyListeners(JEVisEvent event) {
    }

    @Override
    public DateTime getDeleteTS() {
        return null;
    }

    @Override
    public void setDeleteTS(DateTime ts) throws JEVisException {

    }


}
