/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo.costs;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisConstants;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisFile;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.api.JEVisUnit;
import org.jevis.commons.JEVisFileImp;
import org.joda.time.DateTime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * In-memory object tree behind a {@link JEVisDataSource} for the cost demo setup: objects with parents and children,
 * attributes with samples (values and files) and units, creating objects ({@code buildObject} + {@code commit}),
 * writing samples and moving objects to the recycle bin. Every write is recorded in {@link #writes}.
 */
final class FakeTree {

    static final Set<String> FILE_ATTRIBUTES = new HashSet<>(Arrays.asList("Template File", "Data Model File", "Invoice File"));

    final Map<Long, Node> nodes = new LinkedHashMap<>();
    /**
     * every write: "create id class name", "sample id attribute ts", "unit id attribute label", "delete id"
     */
    final List<String> writes = new ArrayList<>();
    /**
     * writes to these object ids fail
     */
    final Set<Long> failOn = new HashSet<>();
    /**
     * creating objects of these classes fails
     */
    final Set<String> failCreate = new HashSet<>();
    private final Map<String, JEVisClass> classes = new HashMap<>();
    private long nextId = 800000;
    final JEVisDataSource ds;

    FakeTree() {
        ds = FakeData.proxy(JEVisDataSource.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getObject": {
                    Node n = nodes.get((Long) args[0]);
                    return n != null && !n.deleted ? n.proxy : null;
                }
                case "getJEVisClass":
                    return jevisClass((String) args[0]);
                case "getObjects": {
                    if (args == null || args.length != 2) return FakeData.standard(proxy, method, args);
                    String name = ((JEVisClass) args[0]).getName();
                    List<JEVisObject> list = new ArrayList<>();
                    for (Node n : nodes.values()) if (!n.deleted && n.jevisClass.equals(name)) list.add(n.proxy);
                    return list;
                }
                case "deleteObject": {
                    long id = (Long) args[0];
                    boolean forever = (Boolean) args[1];
                    Node n = nodes.get(id);
                    if (n == null || n.deleted) return false;
                    if (failOn.contains(id)) throw new JEVisException("delete of " + id + " failed", 500);
                    writes.add((forever ? "deleteForever " : "delete ") + id);
                    n.markDeleted();
                    return true;
                }
                default:
                    return FakeData.standard(proxy, method, args);
            }
        });
    }

    JEVisClass jevisClass(String name) {
        return classes.computeIfAbsent(name, n -> FakeData.proxy(JEVisClass.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return n;
                default:
                    return FakeData.standard(p, method, args);
            }
        }));
    }

    Node add(long id, String name, String jevisClass, Node parent) {
        Node n = new Node(id, name, jevisClass, parent);
        nodes.put(id, n);
        if (parent != null) parent.children.add(n);
        return n;
    }

    Node add(String name, String jevisClass, Node parent) {
        return add(nextId++, name, jevisClass, parent);
    }

    Node node(long id) {
        return nodes.get(id);
    }

    /**
     * @return the (not deleted) child with the name, null if none
     */
    Node child(Node parent, String name) {
        for (Node c : parent.children) if (!c.deleted && c.name.equals(name)) return c;
        return null;
    }

    Node path(Node root, String path) {
        String[] parts = path.split(" / ");
        Node x = root;
        for (int i = 1; i < parts.length && x != null; i++) x = child(x, parts[i]);
        return x;
    }

    int writeCount() {
        return writes.size();
    }

    final class Node {
        Long id;
        final String name;
        final String jevisClass;
        Node parent;
        final List<Node> children = new ArrayList<>();
        final Map<String, Att> attributes = new LinkedHashMap<>();
        boolean deleted;
        final JEVisObject proxy;

        Node(Long id, String name, String jevisClass, Node parent) {
            this.id = id;
            this.name = name;
            this.jevisClass = jevisClass;
            this.parent = parent;
            this.proxy = FakeData.proxy(JEVisObject.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "getID":
                        return this.id;
                    case "getName":
                        return this.name;
                    case "getJEVisClassName":
                        return this.jevisClass;
                    case "getJEVisClass":
                        return jevisClass(this.jevisClass);
                    case "getParents": {
                        List<JEVisObject> list = new ArrayList<>();
                        if (this.parent != null && !this.parent.deleted) list.add(this.parent.proxy);
                        return list;
                    }
                    case "getParent":
                        return this.parent != null ? this.parent.proxy : null;
                    case "getChildren": {
                        List<JEVisObject> list = new ArrayList<>();
                        for (Node c : children) if (!c.deleted) list.add(c.proxy);
                        return list;
                    }
                    case "getAttribute":
                        if (args != null && args.length == 1 && args[0] instanceof String) return att((String) args[0]).proxy;
                        return null;
                    case "getAttributes": {
                        List<JEVisAttribute> list = new ArrayList<>();
                        for (Att a : attributes.values()) list.add(a.proxy);
                        return list;
                    }
                    case "buildObject": {
                        String childName = (String) args[0];
                        String childClass = ((JEVisClass) args[1]).getName();
                        return new Node(null, childName, childClass, this).proxy;
                    }
                    case "commit":
                        if (this.id == null) {
                            if (failCreate.contains(this.jevisClass) || failOn.contains(this.parent.id)) {
                                throw new JEVisException("create of " + this.jevisClass + " '" + this.name + "' failed", 500);
                            }
                            this.id = nextId++;
                            nodes.put(this.id, this);
                            this.parent.children.add(this);
                            writes.add("create " + this.id + " " + this.jevisClass + " " + this.name);
                        }
                        return null;
                    default:
                        return FakeData.standard(p, method, args);
                }
            });
        }

        void markDeleted() {
            deleted = true;
            for (Node c : children) c.markDeleted();
        }

        Att att(String attribute) {
            return attributes.computeIfAbsent(attribute, a -> new Att(this, a));
        }

        Node set(String attribute, DateTime ts, Object value) {
            att(attribute).samples.put(ts.getMillis(), new Object[]{value, null});
            return this;
        }

        Node file(String attribute, DateTime ts, String fileName, String content) {
            try {
                return set(attribute, ts, new JEVisFileImp(fileName, content.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        Object latest(String attribute) {
            Att a = attributes.get(attribute);
            return a == null || a.samples.isEmpty() ? null : a.samples.lastEntry().getValue()[0];
        }

        String latestFile(String attribute) {
            Object v = latest(attribute);
            return v instanceof JEVisFile ? new String(((JEVisFile) v).getBytes(), StandardCharsets.UTF_8) : null;
        }

        @Override
        public String toString() {
            return jevisClass + " '" + name + "' [" + id + "]";
        }
    }

    final class Att {
        final Node owner;
        final String name;
        final NavigableMap<Long, Object[]> samples = new TreeMap<>();
        JEVisUnit inputUnit;
        JEVisUnit displayUnit;
        final JEVisAttribute proxy;

        Att(Node owner, String name) {
            this.owner = owner;
            this.name = name;
            this.proxy = FakeData.proxy(JEVisAttribute.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "getName":
                        return this.name;
                    case "getObject":
                        return owner.proxy;
                    case "getObjectID":
                        return owner.id;
                    case "getPrimitiveType":
                        return FILE_ATTRIBUTES.contains(this.name) ? JEVisConstants.PrimitiveType.FILE : JEVisConstants.PrimitiveType.STRING;
                    case "getAllSamples":
                        return list(samples);
                    case "getSamples":
                        if (args != null && args.length == 2) {
                            long from = ((DateTime) args[0]).getMillis();
                            long to = ((DateTime) args[1]).getMillis();
                            if (to < from) return new ArrayList<>();
                            return list(samples.subMap(from, true, to, true));
                        }
                        return FakeData.standard(p, method, args);
                    case "getLatestSample":
                        return samples.isEmpty() ? null : sample(samples.lastKey(), samples.lastEntry().getValue(), false);
                    case "hasSample":
                        return !samples.isEmpty();
                    case "getTimestampOfFirstSample":
                        return samples.isEmpty() ? null : new DateTime(samples.firstKey());
                    case "getTimestampOfLastSample":
                        return samples.isEmpty() ? null : new DateTime(samples.lastKey());
                    case "deleteAllSample":
                        check();
                        samples.clear();
                        writes.add("deleteSamples " + owner.id + " " + this.name);
                        return true;
                    case "getSampleCount":
                        return (long) samples.size();
                    case "buildSample": {
                        String note = args.length >= 3 && args[2] instanceof String ? (String) args[2] : null;
                        return sample(((DateTime) args[0]).getMillis(), new Object[]{args[1], note}, true);
                    }
                    case "addSamples": {
                        @SuppressWarnings("unchecked")
                        List<JEVisSample> list = (List<JEVisSample>) args[0];
                        for (JEVisSample s : list) ((SampleHandler) Proxy.getInvocationHandler(s)).store();
                        return list.size();
                    }
                    case "getInputUnit":
                        return inputUnit;
                    case "getDisplayUnit":
                        return displayUnit;
                    case "setInputUnit":
                        inputUnit = (JEVisUnit) args[0];
                        return null;
                    case "setDisplayUnit":
                        displayUnit = (JEVisUnit) args[0];
                        return null;
                    case "commit":
                        check();
                        writes.add("unit " + owner.id + " " + this.name + " " + (inputUnit != null ? inputUnit.getLabel() : null));
                        return null;
                    default:
                        return FakeData.standard(p, method, args);
                }
            });
        }

        void check() throws JEVisException {
            if (failOn.contains(owner.id)) throw new JEVisException("write to " + owner.id + " failed", 500);
        }

        private List<JEVisSample> list(Map<Long, Object[]> map) {
            List<JEVisSample> list = new ArrayList<>();
            for (Map.Entry<Long, Object[]> e : map.entrySet()) list.add(sample(e.getKey(), e.getValue(), false));
            return list;
        }

        JEVisSample sample(long ts, Object[] data, boolean built) {
            SampleHandler handler = new SampleHandler(this, ts, data, built);
            return (JEVisSample) Proxy.newProxyInstance(FakeTree.class.getClassLoader(), new Class<?>[]{JEVisSample.class}, handler);
        }
    }

    final class SampleHandler implements InvocationHandler {
        final Att att;
        final long ts;
        final Object[] data;
        final boolean built;

        SampleHandler(Att att, long ts, Object[] data, boolean built) {
            this.att = att;
            this.ts = ts;
            this.data = data;
            this.built = built;
        }

        void store() throws JEVisException {
            att.check();
            att.samples.put(ts, new Object[]{data[0], data[1]});
            writes.add("sample " + att.owner.id + " " + att.name + " " + new DateTime(ts));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object value = data[0];
            switch (method.getName()) {
                case "getTimestamp":
                    return new DateTime(ts);
                case "getValue":
                    return value;
                case "getValueAsString":
                    return value instanceof JEVisFile ? ((JEVisFile) value).getFilename() : value != null ? String.valueOf(value) : null;
                case "getValueAsDouble":
                    return value instanceof Number ? ((Number) value).doubleValue() : value != null ? Double.valueOf(String.valueOf(value)) : null;
                case "getValueAsLong":
                    return value instanceof Number ? ((Number) value).longValue() : value != null ? Long.valueOf(String.valueOf(value)) : null;
                case "getValueAsBoolean":
                    return value instanceof Boolean ? value : value != null ? Boolean.valueOf(String.valueOf(value)) : null;
                case "getValueAsFile":
                    return value instanceof JEVisFile ? value : null;
                case "getNote":
                    return data[1];
                case "getAttribute":
                    return att.proxy;
                case "commit":
                    if (built) store();
                    return null;
                default:
                    return FakeData.standard(proxy, method, args);
            }
        }
    }
}
