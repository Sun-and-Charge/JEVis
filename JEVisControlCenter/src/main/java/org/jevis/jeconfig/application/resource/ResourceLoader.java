/**
 * Copyright (C) 2014 Envidatec GmbH <info@envidatec.com>
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JEApplication.
 *
 * JEApplication is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 *
 * JEApplication is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JEApplication. If not, see <http://www.gnu.org/licenses/>.
 *
 * JEApplication is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.application.resource;

import org.jevis.jeconfig.Icon;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 *
 * @author fs
 */
public class ResourceLoader {

    public static String getResource(String file) {
        return ResourceLoader.class.getResource("/styles/" + file).toExternalForm();
    }

    public static Image getImage(String icon) {
        try {
            if (icon.startsWith("/icons/")) {
                return new Image(ResourceLoader.class.getResourceAsStream(icon));
            }
            return new Image(ResourceLoader.class.getResourceAsStream("/icons/" + icon));
        } catch (Exception ex) {
            return new Image(ResourceLoader.class.getResourceAsStream(Icon.PNG_IMAGE_MISSING));
        }
    }

    /**
     *
     * @param icon
     * @param height
     * @param width
     * @return
     */
    public static ImageView getImage(String icon, double height, double width) {
        ImageView image = new ImageView(ResourceLoader.getImage(icon));
        image.fitHeightProperty().set(height);
        image.fitWidthProperty().set(width);
        return image;
    }
}
