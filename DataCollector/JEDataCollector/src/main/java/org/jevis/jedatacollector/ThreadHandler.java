/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEDataCollector.
 * <p>
 * JEDataCollector is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEDataCollector is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDataCollector. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDataCollector is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedatacollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jevis.api.JEVisObject;

/**
 *
 * @author bf
 */
public class ThreadHandler {

    private List<JEVisObject> _requests = Collections.synchronizedList(new ArrayList());
    private static List<Long> _activeThreads = Collections.synchronizedList(new ArrayList());

    public ThreadHandler(List<JEVisObject> requests) {
        for (JEVisObject object : requests) {
            _requests.add(object);
        }
    }

    synchronized public boolean hasRequest() {
        if (_requests.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    synchronized public JEVisObject getNextDataSource() {
        for (JEVisObject currentReq : _requests) {
            _requests.remove(currentReq);
            return currentReq;
        }
        return null;
    }

    synchronized public int getNumberActiveRequests() {
        return _activeThreads.size();
    }

    synchronized public void removeActiveRequest(Long threadID) {
        _activeThreads.remove(threadID);
    }

    synchronized public void addActiveThread(long threadid) {
        _activeThreads.add(threadid);
    }

    synchronized public List<Long> getActiveThreads() {
        return _activeThreads;
    }
}
