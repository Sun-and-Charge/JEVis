/*
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JEVis. JEVis is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation in version 3. JEVis is distributed in
 * the hope that it will be useful, but WITHOUT ANY WARRANTY; see the GNU General Public License for more details.
 */
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

import java.io.File;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Drawing of the class icons (J25.5): white Material Symbol on a rounded tile in the category colour. */
public class IconArt {
    static File glyphDir;
    /** filled variants, preferred: they keep their weight at the 16-20px of the tree */
    static File fillDir;

    static SVGPath glyph(String name, Color fill) throws Exception {
        File file = fillDir != null && new File(fillDir, name + ".svg").exists() ? new File(fillDir, name + ".svg") : new File(glyphDir, name + ".svg");
        String svg = Files.readString(file.toPath());
        Matcher m = Pattern.compile(" d=\"([^\"]+)\"").matcher(svg);
        StringBuilder d = new StringBuilder();
        while (m.find()) d.append(m.group(1)).append(' ');
        SVGPath p = new SVGPath();
        p.setContent(d.toString());
        p.setFill(fill);
        return p;
    }

    /** places the glyph centred in a box of the given size at (x, y) */
    static Node place(SVGPath p, double x, double y, double size) {
        javafx.geometry.Bounds b = p.getBoundsInLocal();
        double s = size / Math.max(b.getWidth(), b.getHeight());
        p.setScaleX(s);
        p.setScaleY(s);
        Group g = new Group(p);
        javafx.geometry.Bounds gb = g.getBoundsInParent();
        g.setLayoutX(x + (size - gb.getWidth()) / 2 - gb.getMinX());
        g.setLayoutY(y + (size - gb.getHeight()) / 2 - gb.getMinY());
        return g;
    }

    static Rectangle tileShape(Color colour, double size) {
        Rectangle r = new Rectangle(size, size);
        r.setArcWidth(size * 0.42);
        r.setArcHeight(size * 0.42);
        r.setFill(colour);
        return r;
    }

    static Pane sized(Pane pane, double size) {
        pane.setPrefSize(size, size);
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);
        return pane;
    }

    /** a class: white glyph on the tile */
    static Pane tile(String glyph, Color colour, double size) throws Exception {
        return sized(new Pane(tileShape(colour, size), place(glyph(glyph, Color.WHITE), size * 0.14, size * 0.14, size * 0.72)), size);
    }

    /** a directory: folder silhouette in the category colour, the glyph of its children in white inside */
    static Pane folder(String child, Color colour, double size) throws Exception {
        Pane pane = new Pane(place(glyph("folder", colour), 0, 0, size));
        pane.getChildren().add(place(glyph(child, Color.WHITE), size * 0.27, size * 0.32, size * 0.46));
        return sized(pane, size);
    }


}
