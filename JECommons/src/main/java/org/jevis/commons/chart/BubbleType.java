/**
 * Copyright (C) JEVis contributors
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
package org.jevis.commons.chart;

import java.util.ArrayList;
import java.util.List;

public enum BubbleType {
    NONE, X, Y, SIZE;

    public static BubbleType parseBubbleType(String bubbleType) {
        switch (bubbleType) {
            case ("X"):
                return X;
            case ("Y"):
                return Y;
            case ("SIZE"):
                return SIZE;
            default:
                return NONE;
        }
    }

    public static Integer parseBubbleIndex(BubbleType bubbleType) {
        if (bubbleType != null) {
            switch (bubbleType.toString()) {
                default:
                    return 0;
                case ("X"):
                    return 1;
                case ("Y"):
                    return 2;
                case ("SIZE"):
                    return 3;

            }
        }
        return 0;
    }

    public static BubbleType parseBubbleIndex(Integer bubbleIndex) {
        if (bubbleIndex != null) {
            switch (bubbleIndex) {
                default:
                    return NONE;
                case (1):
                    return X;
                case (2):
                    return Y;
                case (3):
                    return SIZE;

            }
        }
        return NONE;
    }

    public static List<String> getListNamesBubbleTypes() {
        List<String> tempList = new ArrayList<>();
        for (BubbleType bt : BubbleType.values()) {
            tempList.add(bt.toString());
        }

        return tempList;
    }

    @Override
    public String toString() {
        switch (this) {
            default:
            case NONE:
                return "NONE";
            case X:
                return "X";
            case Y:
                return "Y";
            case SIZE:
                return "SIZE";
        }
    }
}
