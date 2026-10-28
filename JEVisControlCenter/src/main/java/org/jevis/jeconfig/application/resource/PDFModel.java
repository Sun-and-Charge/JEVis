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
package org.jevis.jeconfig.application.resource;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;

public class PDFModel {
    private static final Logger logger = LogManager.getLogger(PDFModel.class);

    private PDDocument document;
    private PDFRenderer renderer;
    private int pages;

    public PDFModel() {
    }

    public PDFModel(byte[] bytes) {
        setBytes(bytes);
    }

    public void setBytes(byte[] bytes) {
        try {
            document = PDDocument.load(bytes);
            renderer = new PDFRenderer(document);
            pages = document.getPages().getCount();
        } catch (IOException ex) {
            throw new UncheckedIOException("PDDocument throws IOException bytes=" + bytes, ex);
        }
    }

    public int numPages() {
        return pages;
    }

    public ImageView getImage(int pageNumber, double zoom) {
        Image image = getImage(pageNumber);

        ImageView imageView = new ImageView(image);
        imageView.setScaleX(zoom);
        imageView.setScaleY(zoom);

        return imageView;
    }

    private Image getImage(int pageNumber) {
        BufferedImage pageImage;
        try {
            pageImage = renderer.renderImageWithDPI(pageNumber, 300);
        } catch (IOException ex) {
            throw new UncheckedIOException("PDFRenderer throws IOException", ex);
        }

        return SwingFXUtils.toFXImage(pageImage, null);
    }

    public void close() throws IOException {
        renderer = null;
        document.close();
    }
}