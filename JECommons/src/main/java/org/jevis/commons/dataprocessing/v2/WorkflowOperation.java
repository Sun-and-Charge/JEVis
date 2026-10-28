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
package org.jevis.commons.dataprocessing.v2;

import java.awt.image.BufferedImage;

/**
 * Interface for the Dataprocessing Workflow Operations. This operations will
 * allow to implemnt some logic into the workflow like "if Attribute is from
 * Type do .. else .." .
 *
 * @author Florian Simon
 */
public interface WorkflowOperation {

    /**
     * Uniqe id of the operation within this workflow.
     *
     * @return
     */
    public String getID();

    /**
     * Retuns an icon for the GUI Workflow builder.
     *
     * @return
     */
    public BufferedImage getIcon();
}
