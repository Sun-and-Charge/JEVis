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
package org.jevis.jeconfig.tool;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.sun.javafx.scene.control.skin.CustomColorDialog;
import javafx.scene.control.ColorPicker;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;

import java.lang.reflect.Field;

public class ColorPickerFixer {

    public ColorPickerFixer(ColorPicker colorPicker) {

        try {

//            ColorPickerSkin skin = (ColorPickerSkin) upperColorPicker.getSkin();

//            List<Field> privateFields = new ArrayList<>();

            Field[] fields = JFXCheckBox.class.getDeclaredFields();
            for (Field field : fields) {
//                if (Modifier.isPrivate(field.getModifiers())) {
//                    field.setAccessible(true);
                field.setAccessible(true);
//                }
            }
//
//            Field pField = skin.getClass().getField("popupContent");
//            System.out.println("Is accessable: " + pField.isAccessible());
//            pField.setAccessible(true);
//            System.out.println("pField: " + pField.get(skin));
//
//
//            Field linkField = ColorPalette.class.getField("customColorLink");
//
//
//            ColorPalette colorPalette = (ColorPalette) pField.get(skin);
//            Hyperlink hyperlink = (Hyperlink) linkField.get(colorPalette);
//            hyperlink.setOnAction(event -> {
//                System.out.println("hyperlink: " + hyperlink);
//            });

//            ColorPalette colorPalette = (ColorPalette) skin.getClass().getField("popupContent");

//            Field[] allFields = skin.getClass().getDeclaredFields();
//
//            for (Field field : allFields) {
////                if (Modifier.isPrivate(field.getModifiers())) {
//                privateFields.add(field);
////                }
//            }


            JFXButton buttonShowColorPicker = new JFXButton("Show custom color dialog!");
            buttonShowColorPicker.setOnAction(actionEvent -> {
                CustomColorDialog customColorDialog = new CustomColorDialog(JEConfig.getStage());
                customColorDialog.show();
            });

            CustomColorDialog customColorDialog = new CustomColorDialog(JEConfig.getStage());
            customColorDialog.sceneProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    TopMenu.applyActiveTheme(customColorDialog.getScene());
                }
            });
            customColorDialog.show();

//            Stage newStage = new Stage();
//            Scene scene = new Scene(new StackPane(buttonShowColorPicker));
//            newStage.setScene(scene);
//            newStage.show();


//            List<Field> privateFields = new ArrayList<>();
//            Field[] allFields = upperColorPicker.getSkin().getClass().getDeclaredFields();
//            for (Field field : allFields) {
////                if (Modifier.isPrivate(field.getModifiers())) {
//                privateFields.add(field);
////                }
//            }
//            privateFields.forEach(field -> {
//                System.out.println("field: " + field);
//            });

//            Field dialogField = CustomColorDialog.class.getDeclaredField("dialog");
//            dialogField.setAccessible(true);
//            Stage dialog = (Stage) dialogField.get(upperColorPicker);
//            dialog.setAlwaysOnTop(true);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
