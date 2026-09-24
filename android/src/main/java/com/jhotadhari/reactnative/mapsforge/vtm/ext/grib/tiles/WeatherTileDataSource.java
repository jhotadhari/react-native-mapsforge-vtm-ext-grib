package com.jhotadhari.reactnative.mapsforge.vtm.ext.grib.tiles;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.oscim.android.canvas.AndroidBitmap;
import org.oscim.core.BoundingBox;
import org.oscim.layers.tile.MapTile;
import org.oscim.tiling.ITileDataSink;
import org.oscim.tiling.ITileDataSource;
import org.oscim.tiling.QueryResult;

/**
 * Renders weather grid data into bitmap tiles on demand.
 *
 * <p>For each tile request, this class:
 * <ol>
 *   <li>Converts the tile's x/y/zoom to a geographic bounding box.</li>
 *   <li>Iterates over the tile's pixels, mapping each to a lat/lon.</li>
 *   <li>Looks up the weather grid value at that lat/lon.</li>
 *   <li>Maps the value to a color via the configured color ramp.</li>
 *   <li>Returns the rendered {@link Bitmap} to vtm's tile pipeline.</li>
 * </ol>
 */
public class WeatherTileDataSource implements ITileDataSource {

    private final WeatherTileSource tileSource;
    private boolean disposed = false;

    public WeatherTileDataSource(WeatherTileSource tileSource) {
        this.tileSource = tileSource;
    }

    @Override
    public void query(MapTile mapTile, ITileDataSink dataSink) {
        if (disposed) {
            dataSink.completed(QueryResult.FAILED);
            return;
        }

        Bitmap bitmap = null;
        try {
            WeatherGridData grid = tileSource.getGridData();
            if (grid == null || grid.values == null || grid.values.length == 0) {
                dataSink.completed(QueryResult.TILE_NOT_FOUND);
                return;
            }

            String colorMapName = tileSource.getColorMap() != null
                ? tileSource.getColorMap() : "wind";

            // Get the geographic bounds of this tile.
            BoundingBox bbox = tileToBoundingBox(
                mapTile.tileX, mapTile.tileY, mapTile.zoomLevel);

            // Only skip tiles that are completely outside the grid.
            // Use a small epsilon so borderline tiles don't get excluded
            // by floating-point rounding at tile edges.
            final double eps = 0.0001;
            if (bbox.getMaxLatitude() + eps < grid.minLat
                || bbox.getMinLatitude() - eps > grid.maxLat
                || bbox.getMaxLongitude() + eps < grid.minLng
                || bbox.getMinLongitude() - eps > grid.maxLng) {
                dataSink.completed(QueryResult.TILE_NOT_FOUND);
                return;
            }

            int tileSize = tileSource.getTileSize();
            bitmap = Bitmap.createBitmap(
                tileSize, tileSize, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[tileSize * tileSize];

            double dLng = bbox.getMaxLongitude() - bbox.getMinLongitude();
            double dLat = bbox.getMaxLatitude() - bbox.getMinLatitude();

            // Build the color ramp for this parameter.
            ColorRamp ramp = ColorRamp.forName(colorMapName);

            for (int py = 0; py < tileSize; py++) {
                // Sample the center of each pixel cell, not the tile edges.
                // Row 0 = top of tile (maxLat); row (tileSize-1) = bottom.
                double lat = bbox.getMaxLatitude()
                    - ((py + 0.5) / (double) tileSize) * dLat;

                for (int px = 0; px < tileSize; px++) {
                    // Column 0 = left (minLng); column (tileSize-1) = right.
                    double lng = bbox.getMinLongitude()
                        + ((px + 0.5) / (double) tileSize) * dLng;

                    float value = grid.getValueAt(lat, lng);
                    int color;
                    if (Float.isNaN(value)) {
                        color = Color.TRANSPARENT;
                    } else {
                        color = ramp.getColor(value);
                    }

                    pixels[py * tileSize + px] = color;
                }
            }

            bitmap.setPixels(pixels, 0, tileSize, 0, 0, tileSize, tileSize);
            dataSink.setTileImage(new AndroidBitmap(bitmap));
            dataSink.completed(QueryResult.SUCCESS);

        } catch (Exception e) {
            e.printStackTrace();
            // Recycle the bitmap if it was created before the exception.
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            dataSink.completed(QueryResult.FAILED);
        }
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public void cancel() {
        // Non-blocking — nothing to cancel.
    }

    /**
     * Converts a vtm tile coordinate to a WGS84 bounding box.
     *
     * <p>Uses the standard Web Mercator tile scheme: tileX counts east from
     * the antimeridian (-180°), tileY counts south from the north pole.
     */
    private static BoundingBox tileToBoundingBox(
        int tileX, int tileY, byte zoomLevel) {
        long numTiles = 1L << zoomLevel;

        double minLon = (tileX / (double) numTiles) * 360.0 - 180.0;
        double maxLon = ((tileX + 1) / (double) numTiles) * 360.0 - 180.0;

        double n = Math.PI - (2.0 * Math.PI * tileY) / numTiles;
        double maxLat = Math.toDegrees(Math.atan(Math.sinh(n)));

        n = Math.PI - (2.0 * Math.PI * (tileY + 1)) / numTiles;
        double minLat = Math.toDegrees(Math.atan(Math.sinh(n)));

        return new BoundingBox(minLat, minLon, maxLat, maxLon);
    }
}
