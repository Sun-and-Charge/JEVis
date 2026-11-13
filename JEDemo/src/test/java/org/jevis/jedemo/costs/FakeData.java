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
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.jedemo.Ref;
import org.joda.time.DateTime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * An in-memory JEVis data source (dynamic proxies, Mockito cannot mock on Java 25): objects with attributes whose
 * samples are answered by time range. Symbolic keys of the setup plan get ids from 900000 on.
 */
final class FakeData {

    private final Map<Long, Obj> objects = new HashMap<>();
    private final Map<String, Long> keys = new HashMap<>();
    private long nextId = 900000;
    final JEVisDataSource ds;

    FakeData() {
        ds = proxy(JEVisDataSource.class, (proxy, method, args) -> {
            if (method.getName().equals("getObject") && args != null && args.length == 1 && args[0] instanceof Long) {
                Obj o = objects.get(args[0]);
                return o != null ? o.proxy : null;
            }
            return standard(proxy, method, args);
        });
    }

    long id(Ref ref) {
        if (ref.isResolved()) return ref.getId();
        return keys.computeIfAbsent(ref.getKey(), k -> nextId++);
    }

    Obj object(Ref ref, String name, String jevisClass) {
        long id = id(ref);
        return objects.computeIfAbsent(id, i -> new Obj(i, name, jevisClass));
    }

    Obj object(long id) {
        return objects.get(id);
    }

    /**
     * All objects of the setup plan with their samples (settings without time stamp are not needed).
     */
    void addPlan() {
        for (PlanObject o : CostCases.objects()) {
            Obj obj = object(o.ref(), o.getName(), o.getJevisClass());
            for (Map.Entry<String, PlanObject.Attr> e : o.getAttributes().entrySet()) {
                for (PlanObject.Sample s : e.getValue().getSamples()) {
                    obj.add(e.getKey(), new DateTime(s.getTs().toInstant().toEpochMilli()), s.getValue());
                }
            }
        }
    }

    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(FakeData.class.getClassLoader(), new Class<?>[]{type}, handler));
    }

    static Object standard(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "fake " + method.getDeclaringClass().getSimpleName();
            default:
                Class<?> r = method.getReturnType();
                if (r == boolean.class) return false;
                if (r == int.class) return 0;
                if (r == long.class) return 0L;
                if (r == double.class) return 0d;
                if (List.class.isAssignableFrom(r)) return new ArrayList<>();
                return null;
        }
    }

    final class Obj {
        final long id;
        final String name;
        final String jevisClass;
        final Map<String, NavigableMap<Long, JEVisSample>> attributes = new LinkedHashMap<>();
        final JEVisObject proxy;

        Obj(long id, String name, String jevisClass) {
            this.id = id;
            this.name = name;
            this.jevisClass = jevisClass;
            this.proxy = FakeData.proxy(JEVisObject.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "getID":
                        return this.id;
                    case "getName":
                        return this.name;
                    case "getJEVisClassName":
                        return this.jevisClass;
                    case "getAttribute":
                        if (args != null && args.length == 1 && args[0] instanceof String) return attribute((String) args[0]);
                        return null;
                    default:
                        return standard(p, method, args);
                }
            });
        }

        Obj add(String attribute, DateTime ts, Object value) {
            JEVisSample sample;
            if (value instanceof Double) sample = new VirtualSample(ts, (Double) value);
            else if (value instanceof Number) sample = new VirtualSample(ts, ((Number) value).doubleValue());
            else sample = new VirtualSample(ts, String.valueOf(value));
            attributes.computeIfAbsent(attribute, a -> new TreeMap<>()).put(ts.getMillis(), sample);
            return this;
        }

        Obj add(List<History.Point> points) {
            for (History.Point p : points) add("Value", new DateTime(p.getTs().toEpochMilli()), p.getValue());
            return this;
        }

        Obj set(Instant ts, double value) {
            return add("Value", new DateTime(ts.toEpochMilli()), value);
        }

        JEVisAttribute attribute(String name) {
            NavigableMap<Long, JEVisSample> samples = attributes.get(name);
            if (samples == null) return null;
            return FakeData.proxy(JEVisAttribute.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "getSamples":
                        if (args != null && args.length == 2) {
                            long from = ((DateTime) args[0]).getMillis();
                            long to = ((DateTime) args[1]).getMillis();
                            if (to < from) return new ArrayList<>();
                            return new ArrayList<>(samples.subMap(from, true, to, true).values());
                        }
                        return standard(p, method, args);
                    case "getAllSamples":
                        return new ArrayList<>(samples.values());
                    case "getLatestSample":
                        return samples.isEmpty() ? null : samples.lastEntry().getValue();
                    case "hasSample":
                        return !samples.isEmpty();
                    case "getObject":
                        return proxy;
                    case "getName":
                        return name;
                    case "getObjectID":
                        return id;
                    default:
                        return standard(p, method, args);
                }
            });
        }
    }

    static List<Object> list(Object... values) {
        List<Object> list = new ArrayList<>();
        Collections.addAll(list, values);
        return list;
    }
}
