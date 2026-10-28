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
package org.jevis.jeconfig.tool.datepicker;

import com.jfoenix.controls.JFXButton;
import javafx.beans.binding.BooleanBinding;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Control;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.Calendar;

/**
 * The main navigation pane.
 *
 * @author Christian Schudt
 */
final class MainNavigationPane extends HBox {

    private static final String CSS_CALENDAR_NAVIGATION_ARROW = "calendar-navigation-arrow";
    private static final String CSS_CALENDAR_NAVIGATION_BUTTON = "calendar-navigation-button";
    private static final String CSS_CALENDAR_NAVIGATION_TITLE = "calendar-navigation-title";
    private static final String CSS_CALENDAR_HEADER = "calendar-header";

    private final CalendarView calendarView;
    JFXButton titleButton;

    public MainNavigationPane(final CalendarView calendarView) {

        this.calendarView = calendarView;


        titleButton = new JFXButton();
        titleButton.getStyleClass().add(CSS_CALENDAR_NAVIGATION_TITLE);
        titleButton.textProperty().bind(calendarView.title);

        titleButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                switch (calendarView.currentlyViewing.get()) {
                    case Calendar.MONTH:
                        calendarView.currentlyViewing.set(Calendar.YEAR);
                        break;
                    case Calendar.YEAR:
                        calendarView.currentlyViewing.set(Calendar.ERA);
                }
            }
        });
        titleButton.disableProperty().bind(new BooleanBinding() {
            {
                super.bind(calendarView.ongoingTransitions, calendarView.currentlyViewing);
            }

            @Override
            protected boolean computeValue() {
                return calendarView.currentlyViewing.get() == Calendar.ERA || calendarView.ongoingTransitions.get() > 0;
            }
        });
        HBox buttonBox = new HBox();
        buttonBox.getChildren().add(titleButton);
        buttonBox.setAlignment(Pos.CENTER);

        HBox.setHgrow(buttonBox, Priority.ALWAYS);

        getChildren().add(getNavigationButton(-1));
        getChildren().add(buttonBox);
        getChildren().add(getNavigationButton(1));

        getStyleClass().add(CSS_CALENDAR_HEADER);
    }

    /**
     * Gets a navigation button.
     *
     * @param direction Either -1 (for left) or 1 (for right).
     * @return The button.
     */
    private JFXButton getNavigationButton(final int direction) {

        JFXButton button = new JFXButton();

        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                Calendar calendar = calendarView.getCalendar();
                switch (calendarView.currentlyViewing.get()) {
                    case Calendar.MONTH:
                        calendar.add(Calendar.MONTH, 1 * direction);
                        break;
                    case Calendar.YEAR:
                        calendar.add(Calendar.YEAR, 1 * direction);
                        break;
                    case Calendar.ERA:
                        calendar.add(Calendar.YEAR, 20 * direction);
                        break;
                }

                calendarView.calendarDate.set(calendar.getTime());
            }
        });

        // Make a region, so that -fx-shape can be applied from CSS.
        Region rectangle = new Region();
        rectangle.setMaxWidth(Control.USE_PREF_SIZE);
        rectangle.setMaxHeight(Control.USE_PREF_SIZE);
        rectangle.setRotate(direction < 0 ? 90 : 270);
        rectangle.getStyleClass().add(CSS_CALENDAR_NAVIGATION_ARROW);
        // Set that region as the button graphic.
        button.setGraphic(rectangle);
        button.getStyleClass().add(CSS_CALENDAR_NAVIGATION_BUTTON);
        return button;
    }

}
