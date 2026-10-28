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

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

/**
 *
 * @author fs
 */
public class LoadingPane extends StackPane {

    private ProgressIndicator progress;
    private BorderPane processPane = new BorderPane();
    private boolean infiniteProgress = true;
    private AnchorPane _contentView = new AnchorPane();
    private Node _content = new BorderPane();

    public LoadingPane() {
        super();
        init();
    }

    public void setContent(Node content) {
        _content = content;

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                AnchorPane.setTopAnchor(_content, 0.0);
                AnchorPane.setRightAnchor(_content, 0.0);
                AnchorPane.setLeftAnchor(_content, 0.0);
                AnchorPane.setBottomAnchor(_content, 0.0);
                _contentView.getChildren().setAll(_content);
            }
        });

    }

    public void startLoading() {
        //        Platform.runLater(new Runnable() {
        //            @Override
//    public void run() {
//        logger.info("startLoading");
        processPane.setVisible(true);
        progress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
//            }
//        });
    }

    public void endLoading() {
//        Platform.runLater(new Runnable() {
//            @Override
//            public void run() {
        processPane.setVisible(false);
        progress.setProgress(100.0);
//            }
//        });
    }

    private void init() {
        progress = new ProgressIndicator();
        progress.setMaxSize(80, 80);
        processPane.setCenter(progress);
        processPane.setStyle("-fx-background-color: transparent");
//        processPane.setOpacity(0.7);
//        setOpacity(0.7);
//        setStyle("-fx-background-color: transparent");

        getChildren().add(_contentView);
        getChildren().add(processPane);

        processPane.setOpacity(0.8);
        processPane.setVisible(false);
    }

}
