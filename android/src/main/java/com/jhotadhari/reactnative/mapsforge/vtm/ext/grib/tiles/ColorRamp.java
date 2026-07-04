package com.jhotadhari.reactnative.mapsforge.vtm.ext.grib.tiles;

import android.graphics.Color;

/**
 * Maps numeric weather values to display colors.
 *
 * <p>Built-in ramps follow meteorological conventions:
 * <ul>
 *   <li><b>temperature</b> — blue → cyan → green → yellow → orange → red</li>
 *   <li><b>wind</b> — light blue → green → yellow → red → purple</li>
 *   <li><b>pressure</b> — blue → white → red (diverging, centered on 1013 hPa)</li>
 *   <li><b>rainfall</b> — transparent → light blue → blue → purple</li>
 *   <li><b>waves</b> — light cyan → blue → dark blue</li>
 *   <li><b>grayscale</b> — black → white (generic, for debugging)</li>
 * </ul>
 */
public class ColorRamp {

    /** Color stops: [{value, r, g, b}, ...]. Sorted by value ascending. */
    private final float[] values;
    private final int[] colors;

    private ColorRamp(float[] values, int[] colors) {
        this.values = values;
        this.colors = colors;
    }

    /**
     * Returns the color for a given data value, interpolating between
     * the nearest stops.
     */
    public int getColor(float value) {
        if (value <= values[0]) return colors[0];
        if (value >= values[values.length - 1]) return colors[colors.length - 1];

        // Find the bracket.
        int i = 0;
        while (i < values.length - 1 && values[i + 1] < value) {
            i++;
        }

        float t = (value - values[i]) / (values[i + 1] - values[i]);
        t = Math.max(0, Math.min(1, t));

        int c0 = colors[i];
        int c1 = colors[i + 1];

        int r = (int) (Color.red(c0) + (Color.red(c1) - Color.red(c0)) * t);
        int g = (int) (Color.green(c0) + (Color.green(c1) - Color.green(c0)) * t);
        int b = (int) (Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * t);
        int a = (int) (Color.alpha(c0) + (Color.alpha(c1) - Color.alpha(c0)) * t);

        // Per-stop alpha is interpolated along with RGB. The layer-level
        // opacity (set via setOpacity / BitmapTileLayer.setBitmapAlpha)
        // provides the second multiplier, so users can achieve any effective
        // opacity by tuning per-stop alpha and layer opacity independently.
        return Color.argb(a, r, g, b);
    }

    /**
     * Returns a built-in color ramp by name.
     *
     * @param name One of: "temperature", "wind", "pressure", "rainfall", "waves", "grayscale".
     * @return The corresponding ColorRamp, or "wind" if the name is unrecognized.
     */
    public static ColorRamp forName(String name) {
        switch (name != null ? name.toLowerCase() : "") {
            case "temperature":
                return temperature();
            case "pressure":
                return pressure();
            case "rainfall":
                return rainfall();
            case "waves":
                return waves();
            case "grayscale":
                return grayscale();
            case "wind":
            default:
                return wind();
        }
    }

    // -- Built-in ramps ---------------------------------------------------

    /** Temperature: -20°C (blue) → 0°C (cyan) → 15°C (green) → 25°C (yellow) → 35°C (red). */
    private static ColorRamp temperature() {
        return new ColorRamp(
            new float[]{-20, 0, 15, 25, 35},
            new int[]{
                Color.rgb(0, 0, 255),     // -20°C: blue
                Color.rgb(0, 200, 255),   // 0°C: cyan
                Color.rgb(0, 200, 0),     // 15°C: green
                Color.rgb(255, 255, 0),   // 25°C: yellow
                Color.rgb(255, 0, 0),     // 35°C: red
            }
        );
    }

    /** Wind speed: 0 m/s (light blue) → 15 m/s (green) → 30 m/s (yellow) → 50 m/s (red) → 70 m/s (purple). */
    private static ColorRamp wind() {
        return new ColorRamp(
            new float[]{0, 15, 30, 50, 70},
            new int[]{
                Color.rgb(200, 230, 255), // calm: pale blue
                Color.rgb(50, 200, 50),   // 15 m/s: green
                Color.rgb(255, 255, 0),   // 30 m/s: yellow
                Color.rgb(255, 0, 0),     // 50 m/s: red
                Color.rgb(128, 0, 128),   // 70 m/s: purple
            }
        );
    }

    /** Pressure: 960 hPa (blue) → 1013 hPa (white) → 1050 hPa (red). Diverging around standard sea-level pressure. */
    private static ColorRamp pressure() {
        return new ColorRamp(
            new float[]{960, 990, 1013, 1036, 1050},
            new int[]{
                Color.rgb(0, 0, 200),     // deep low: blue
                Color.rgb(100, 150, 255), // low: light blue
                Color.rgb(255, 255, 255), // 1013: white
                Color.rgb(255, 150, 100), // high: light red
                Color.rgb(200, 0, 0),     // deep high: red
            }
        );
    }

    /** Rainfall rate: 0 mm/h (transparent) → 1 (light blue) → 5 (blue) → 20 (purple). */
    private static ColorRamp rainfall() {
        return new ColorRamp(
            new float[]{0, 1, 5, 20},
            new int[]{
                Color.argb(40, 200, 230, 255),  // trace: very pale
                Color.rgb(100, 180, 255),        // 1 mm/h: light blue
                Color.rgb(0, 0, 255),            // 5 mm/h: blue
                Color.rgb(100, 0, 200),          // 20 mm/h: purple
            }
        );
    }

    /** Wave height: 0m (cyan) → 2m (blue) → 5m (dark blue) → 10m (very dark). */
    private static ColorRamp waves() {
        return new ColorRamp(
            new float[]{0, 2, 5, 10},
            new int[]{
                Color.rgb(180, 230, 255), // calm: light cyan
                Color.rgb(30, 100, 255),  // 2m: blue
                Color.rgb(0, 0, 120),     // 5m: dark blue
                Color.rgb(0, 0, 40),      // 10m: very dark
            }
        );
    }

    /** Grayscale: 0 (black) → 100 (white). Generic/debug ramp. */
    private static ColorRamp grayscale() {
        return new ColorRamp(
            new float[]{0, 100},
            new int[]{
                Color.rgb(0, 0, 0),
                Color.rgb(255, 255, 255),
            }
        );
    }
}
