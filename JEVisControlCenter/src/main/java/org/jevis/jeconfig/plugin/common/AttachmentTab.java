/**
 * Copyright (C) JEVis contributors
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
package org.jevis.jeconfig.plugin.common;

import org.jevis.api.JEVisObject;

import java.util.function.Function;

/**
 * Form tab with the file attachments of a data object.
 */
public class AttachmentTab<T> extends DataTab<T> {

    private final Function<T, JEVisObject> objectOf;

    public AttachmentTab(String title, Function<T, JEVisObject> objectOf) {
        super(title);
        this.objectOf = objectOf;
    }

    @Override
    public void initTab(T data) {
        notificationPane.setContent(new AttachmentPane(objectOf.apply(data)));
        this.setContent(notificationPane);
    }

    @Override
    public void updateView(T data) {

    }
}
