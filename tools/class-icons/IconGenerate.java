/*
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JEVis. JEVis is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation in version 3. JEVis is distributed in
 * the hope that it will be useful, but WITHOUT ANY WARRANTY; see the GNU General Public License for more details.
 */
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** Renders the class icons from mapping.tsv. Usage: IconGenerate <outlinedDir> <filledDir> <mapping.tsv> <outDir> */
public class IconGenerate extends Application {
    static String mapping, outDir;
    static final double SIZE = 128;

    public static void main(String[] a) {
        IconArt.glyphDir = new File(a[0]);
        IconArt.fillDir = new File(a[1]);
        mapping = a[2];
        outDir = a[3];
        launch(a);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Group root = new Group();
        stage.setScene(new Scene(root, 200, 200));
        stage.show();
        List<String> lines = Files.readAllLines(new File(mapping).toPath());
        int n = 0;
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        for (String line : lines) {
            String[] c = line.split("\t");
            Color colour = Color.web(c[3]);
            Pane icon = "folder".equals(c[1]) ? IconArt.folder(c[2], colour, SIZE) : IconArt.tile(c[2], colour, SIZE);
            root.getChildren().setAll(icon);
            icon.applyCss();
            icon.layout();
            WritableImage img = icon.snapshot(sp, new WritableImage((int) SIZE, (int) SIZE));
            File f = new File(outDir, c[0]);
            f.getParentFile().mkdirs();
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", f);
            n++;
        }
        System.out.println("IconGenerate " + n);
        Platform.exit();
    }
}
