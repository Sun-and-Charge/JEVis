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
package org.jevis.commons.classes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisType;

public class JEVisClassPrinter {
    private static final Logger logger = LogManager.getLogger(JEVisClassPrinter.class);
    private final JEVisDataSource ds;

    public JEVisClassPrinter(JEVisDataSource dataSourceWS) {
        this.ds = dataSourceWS;

    }

    public static String capitalizeWord(String str) {
        String[] words = str.split("\\s");
        String capitalizeWord = "";
        for (String w : words) {
            String first = w.substring(0, 1);
            String afterfirst = w.substring(1);
            capitalizeWord += first.toUpperCase() + afterfirst + " ";
        }
        return capitalizeWord.trim();
    }

    public void printAll() {
        try {

            ds.getJEVisClasses().stream().sorted().forEach(jeVisClass -> {
                try {
                    if (jeVisClass.getInheritance() == null) {
                        printClass(0, jeVisClass);
                    }
                } catch (Exception exception) {

                }
            });
            System.exit(0);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void printClass(int deep, JEVisClass jeVisClass) {

        try {
            printClassClass(jeVisClass);
            String name = jeVisClass.getName();
            for (JEVisClass jeVisClass1 : jeVisClass.getHeirs()) {
                if (!Character.isDigit(jeVisClass1.getName().charAt(0))) {
                    String child = jeVisClass1.getName();
                    printClass(deep, jeVisClass1);
                }
            }
            System.out.println("}");
        } catch (Exception ex) {

        }
    }

    public void printClassClass(JEVisClass jeVisClass) {
        if (jeVisClass != null) {
            try {
                String cName = capitalizeWord(jeVisClass.getName());
                cName = cName.trim().replaceAll(" ", "");
                cName = cName.replaceAll("-", "");
                String s = String.format("public interface %s {\n" +
                                "        public static String name = \"%s\";\n"
                        , cName, jeVisClass.getName());

                for (JEVisType jeVisType : jeVisClass.getTypes()) {
                    if (!jeVisType.isInherited()) {
                        String aName = capitalizeWord(jeVisType.getName());
                        aName = aName.trim().replaceAll(" ", "");
                        aName = aName.replaceAll("-", "");
                        s += String.format("\n        public static String a_%s = \"%s\";", aName, jeVisType.getName());
                    } else {
                        String aName = capitalizeWord(jeVisType.getName());
                        aName = aName.trim().replaceAll(" ", "");
                        s += String.format("\n        public static String i_%s = \"%s\";", aName, jeVisType.getName());
                    }
                }

                logger.debug(s);

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
