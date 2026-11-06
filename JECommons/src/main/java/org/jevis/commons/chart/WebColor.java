/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.chart;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses color strings like JavaFX {@code Color.web(String)} does (as used by the Control Center to read the
 * colors of an analysis) without depending on JavaFX, and formats them as {@code "#rrggbb"}.
 * <p>
 * Supported: {@code #rgb}, {@code #rgba}, {@code #rrggbb}, {@code #rrggbbaa}, the same with {@code 0x} or without
 * prefix, {@code rgb(...)}/{@code rgba(...)} with numbers or percentages and the named CSS colors of JavaFX.
 * The alpha channel is ignored in the output.
 */
public final class WebColor {

    /**
     * Default color of a data row in the Control Center ({@code Color.LIGHTBLUE}).
     */
    public static final String DEFAULT_ROW_COLOR = "#add8e6";
    private static final Map<String, Integer> NAMED = new HashMap<>();

    static {
        String[] named = {
                "aliceblue", "f0f8ff", "antiquewhite", "faebd7", "aqua", "00ffff", "aquamarine", "7fffd4",
                "azure", "f0ffff", "beige", "f5f5dc", "bisque", "ffe4c4", "black", "000000",
                "blanchedalmond", "ffebcd", "blue", "0000ff", "blueviolet", "8a2be2", "brown", "a52a2a",
                "burlywood", "deb887", "cadetblue", "5f9ea0", "chartreuse", "7fff00", "chocolate", "d2691e",
                "coral", "ff7f50", "cornflowerblue", "6495ed", "cornsilk", "fff8dc", "crimson", "dc143c",
                "cyan", "00ffff", "darkblue", "00008b", "darkcyan", "008b8b", "darkgoldenrod", "b8860b",
                "darkgray", "a9a9a9", "darkgreen", "006400", "darkgrey", "a9a9a9", "darkkhaki", "bdb76b",
                "darkmagenta", "8b008b", "darkolivegreen", "556b2f", "darkorange", "ff8c00", "darkorchid", "9932cc",
                "darkred", "8b0000", "darksalmon", "e9967a", "darkseagreen", "8fbc8f", "darkslateblue", "483d8b",
                "darkslategray", "2f4f4f", "darkslategrey", "2f4f4f", "darkturquoise", "00ced1", "darkviolet", "9400d3",
                "deeppink", "ff1493", "deepskyblue", "00bfff", "dimgray", "696969", "dimgrey", "696969",
                "dodgerblue", "1e90ff", "firebrick", "b22222", "floralwhite", "fffaf0", "forestgreen", "228b22",
                "fuchsia", "ff00ff", "gainsboro", "dcdcdc", "ghostwhite", "f8f8ff", "gold", "ffd700",
                "goldenrod", "daa520", "gray", "808080", "green", "008000", "greenyellow", "adff2f",
                "grey", "808080", "honeydew", "f0fff0", "hotpink", "ff69b4", "indianred", "cd5c5c",
                "indigo", "4b0082", "ivory", "fffff0", "khaki", "f0e68c", "lavender", "e6e6fa",
                "lavenderblush", "fff0f5", "lawngreen", "7cfc00", "lemonchiffon", "fffacd", "lightblue", "add8e6",
                "lightcoral", "f08080", "lightcyan", "e0ffff", "lightgoldenrodyellow", "fafad2", "lightgray", "d3d3d3",
                "lightgreen", "90ee90", "lightgrey", "d3d3d3", "lightpink", "ffb6c1", "lightsalmon", "ffa07a",
                "lightseagreen", "20b2aa", "lightskyblue", "87cefa", "lightslategray", "778899", "lightslategrey", "778899",
                "lightsteelblue", "b0c4de", "lightyellow", "ffffe0", "lime", "00ff00", "limegreen", "32cd32",
                "linen", "faf0e6", "magenta", "ff00ff", "maroon", "800000", "mediumaquamarine", "66cdaa",
                "mediumblue", "0000cd", "mediumorchid", "ba55d3", "mediumpurple", "9370db", "mediumseagreen", "3cb371",
                "mediumslateblue", "7b68ee", "mediumspringgreen", "00fa9a", "mediumturquoise", "48d1cc", "mediumvioletred", "c71585",
                "midnightblue", "191970", "mintcream", "f5fffa", "mistyrose", "ffe4e1", "moccasin", "ffe4b5",
                "navajowhite", "ffdead", "navy", "000080", "oldlace", "fdf5e6", "olive", "808000",
                "olivedrab", "6b8e23", "orange", "ffa500", "orangered", "ff4500", "orchid", "da70d6",
                "palegoldenrod", "eee8aa", "palegreen", "98fb98", "paleturquoise", "afeeee", "palevioletred", "db7093",
                "papayawhip", "ffefd5", "peachpuff", "ffdab9", "peru", "cd853f", "pink", "ffc0cb",
                "plum", "dda0dd", "powderblue", "b0e0e6", "purple", "800080", "red", "ff0000",
                "rosybrown", "bc8f8f", "royalblue", "4169e1", "saddlebrown", "8b4513", "salmon", "fa8072",
                "sandybrown", "f4a460", "seagreen", "2e8b57", "seashell", "fff5ee", "sienna", "a0522d",
                "silver", "c0c0c0", "skyblue", "87ceeb", "slateblue", "6a5acd", "slategray", "708090",
                "slategrey", "708090", "snow", "fffafa", "springgreen", "00ff7f", "steelblue", "4682b4",
                "tan", "d2b48c", "teal", "008080", "thistle", "d8bfd8", "tomato", "ff6347",
                "transparent", "000000", "turquoise", "40e0d0", "violet", "ee82ee", "wheat", "f5deb3",
                "white", "ffffff", "whitesmoke", "f5f5f5", "yellow", "ffff00", "yellowgreen", "9acd32"
        };
        for (int i = 0; i < named.length; i += 2) {
            NAMED.put(named[i], Integer.parseInt(named[i + 1], 16));
        }
    }

    private WebColor() {
    }

    /**
     * Parses a color like {@code Color.web(String)}.
     *
     * @param color the color string, e.g. {@code "0x00538aff"} (format written by the Control Center), {@code "#00538a"}
     * @return the color as {@code "#rrggbb"} (lower case)
     * @throws IllegalArgumentException if the string is no valid color (like {@code Color.web})
     */
    public static String toHex(String color) {
        if (color == null) throw new NullPointerException("color");
        String c = color.trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty()) throw new IllegalArgumentException("Invalid color specification");

        int offset;
        if (c.startsWith("#")) {
            offset = 1;
        } else if (c.startsWith("0x")) {
            offset = 2;
        } else if (c.startsWith("rgb")) {
            return parseRgbFunction(c);
        } else {
            Integer rgb = NAMED.get(c);
            if (rgb != null) return format(rgb >> 16 & 0xff, rgb >> 8 & 0xff, rgb & 0xff);
            offset = 0;
        }

        String hex = c.substring(offset);
        try {
            switch (hex.length()) {
                case 3:
                case 4:
                    return format(Integer.parseInt(hex.substring(0, 1), 16) * 17,
                            Integer.parseInt(hex.substring(1, 2), 16) * 17,
                            Integer.parseInt(hex.substring(2, 3), 16) * 17);
                case 6:
                case 8:
                    return format(Integer.parseInt(hex.substring(0, 2), 16),
                            Integer.parseInt(hex.substring(2, 4), 16),
                            Integer.parseInt(hex.substring(4, 6), 16));
                default:
                    break;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid color specification: " + color, e);
        }
        throw new IllegalArgumentException("Invalid color specification: " + color);
    }

    /**
     * Like {@link #toHex(String)} but returns {@code fallback} instead of throwing.
     */
    public static String toHex(String color, String fallback) {
        try {
            return toHex(color);
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Parses a color like {@link #toHex(String)} but keeps the alpha channel.
     *
     * @return {@code "#rrggbb"} for opaque colors, {@code "rgba(r,g,b,a)"} otherwise ({@code "transparent"} is
     * {@code "rgba(0,0,0,0)"})
     * @throws IllegalArgumentException if the string is no valid color
     */
    public static String toCss(String color) {
        if (color == null) throw new NullPointerException("color");
        String hex = toHex(color);
        double alpha = alpha(color.trim().toLowerCase(Locale.ROOT));
        if (alpha >= 1d) return hex;
        int rgb = Integer.parseInt(hex.substring(1), 16);
        String a = java.math.BigDecimal.valueOf(Math.round(Math.max(0d, alpha) * 1000d), 3).stripTrailingZeros().toPlainString();
        return "rgba(" + (rgb >> 16 & 0xff) + "," + (rgb >> 8 & 0xff) + "," + (rgb & 0xff) + "," + a + ")";
    }

    /**
     * Like {@link #toCss(String)} but returns {@code fallback} instead of throwing (also for {@code null}).
     */
    public static String toCss(String color, String fallback) {
        try {
            return toCss(color);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double alpha(String c) {
        if ("transparent".equals(c)) return 0d;
        if (c.startsWith("rgba") || c.startsWith("hsla")) {
            int open = c.indexOf('(');
            int close = c.lastIndexOf(')');
            String[] parts = c.substring(open + 1, close).split(",");
            if (parts.length < 4) return 1d;
            String part = parts[3].trim();
            double value = part.endsWith("%") ? Double.parseDouble(part.substring(0, part.length() - 1)) / 100d
                    : Double.parseDouble(part);
            return Math.max(0d, Math.min(1d, value));
        }
        String hex;
        if (c.startsWith("#")) hex = c.substring(1);
        else if (c.startsWith("0x")) hex = c.substring(2);
        else if (NAMED.containsKey(c)) return 1d;
        else hex = c;
        if (hex.length() == 4) return Integer.parseInt(hex.substring(3, 4), 16) * 17 / 255d;
        if (hex.length() == 8) return Integer.parseInt(hex.substring(6, 8), 16) / 255d;
        return 1d;
    }

    private static String parseRgbFunction(String c) {
        int open = c.indexOf('(');
        int close = c.lastIndexOf(')');
        if (open < 0 || close < open) throw new IllegalArgumentException("Invalid color specification: " + c);
        String[] parts = c.substring(open + 1, close).split(",");
        if (parts.length < 3) throw new IllegalArgumentException("Invalid color specification: " + c);
        int[] rgb = new int[3];
        for (int i = 0; i < 3; i++) {
            String part = parts[i].trim();
            double value;
            if (part.endsWith("%")) {
                value = Double.parseDouble(part.substring(0, part.length() - 1)) / 100d;
            } else {
                value = Double.parseDouble(part) / 255d;
            }
            value = Math.max(0d, Math.min(1d, value));
            rgb[i] = (int) Math.round(value * 255d);
        }
        return format(rgb[0], rgb[1], rgb[2]);
    }

    private static String format(int r, int g, int b) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b);
    }
}
