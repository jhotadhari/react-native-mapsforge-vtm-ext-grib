package com.jhotadhari.reactnative.mapsforge.vtm.ext.grib.tiles;

/**
 * In-memory representation of a single time step of gridded weather data.
 *
 * <p>The grid is a regular lat/lon grid with {@code ni} columns (longitude)
 * and {@code nj} rows (latitude). Values are stored in row-major order
 * (latitude varies slowest). Missing values are represented by {@link Float#NaN}.
 */
public class WeatherGridData {

    /** Minimum latitude (southern edge). */
    public final double minLat;
    /** Maximum latitude (northern edge). */
    public final double maxLat;
    /** Minimum longitude (western edge). */
    public final double minLng;
    /** Maximum longitude (eastern edge). */
    public final double maxLng;
    /** Number of grid points in the longitude direction. */
    public final int ni;
    /** Number of grid points in the latitude direction. */
    public final int nj;
    /** Row-major grid values, length = ni * nj. NaN = missing. */
    public final float[] values;

    /** The human-readable parameter name (e.g. "WIND", "TEMP"). */
    public final String parameter;
    /** The unit string (e.g. "m/s", "K", "hPa"). */
    public final String unit;

    /**
     * @param minLat  Minimum latitude (southern edge).
     * @param maxLat  Maximum latitude (northern edge).
     * @param minLng  Minimum longitude (western edge).
     * @param maxLng  Maximum longitude (eastern edge).
     * @param ni      Number of grid points in longitude direction.
     * @param nj      Number of grid points in latitude direction.
     * @param values  Row-major float values.
     * @param parameter Parameter name.
     * @param unit    Unit string.
     */
    public WeatherGridData(
        double minLat, double maxLat,
        double minLng, double maxLng,
        int ni, int nj,
        float[] values,
        String parameter,
        String unit
    ) {
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLng = minLng;
        this.maxLng = maxLng;
        this.ni = ni;
        this.nj = nj;
        this.values = values;
        this.parameter = parameter;
        this.unit = unit;
    }

    /**
     * Looks up the grid value at a given geographic position using bilinear
     * interpolation. Returns {@link Float#NaN} if the position is outside the
     * grid bounds or if any of the four surrounding grid points are missing.
     */
    public float getValueAt(double lat, double lng) {
        if (lat < minLat || lat > maxLat || lng < minLng || lng > maxLng) {
            return Float.NaN;
        }

        // Fractional position within the grid [0..1].
        double fx = (lng - minLng) / (maxLng - minLng);
        double fy = (lat - minLat) / (maxLat - minLat);

        // Grid index (floating point).
        double gi = fx * (ni - 1);
        double gj = (1.0 - fy) * (nj - 1); // Flip: row 0 is maxLat.

        int i0 = (int) Math.floor(gi);
        int i1 = Math.min(i0 + 1, ni - 1);
        int j0 = (int) Math.floor(gj);
        int j1 = Math.min(j0 + 1, nj - 1);

        float v00 = values[j0 * ni + i0];
        float v10 = values[j0 * ni + i1];
        float v01 = values[j1 * ni + i0];
        float v11 = values[j1 * ni + i1];

        // If any surrounding point is missing, don't interpolate.
        if (Float.isNaN(v00) || Float.isNaN(v10) || Float.isNaN(v01) || Float.isNaN(v11)) {
            return Float.NaN;
        }

        double tx = gi - i0;
        double ty = gj - j0;

        // Bilinear interpolation.
        float v0 = (float) (v00 + (v10 - v00) * tx);
        float v1 = (float) (v01 + (v11 - v01) * tx);
        return (float) (v0 + (v1 - v0) * ty);
    }
}
