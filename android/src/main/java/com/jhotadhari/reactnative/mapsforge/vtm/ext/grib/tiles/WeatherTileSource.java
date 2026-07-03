package com.jhotadhari.reactnative.mapsforge.vtm.ext.grib.tiles;

import org.oscim.tiling.ITileDataSource;
import org.oscim.tiling.TileSource;

/**
 * A vtm {@link TileSource} that renders weather grid data as colored bitmap tiles.
 *
 * <p>Extends {@link TileSource} directly — no URL, no HTTP. The data lives in
 * memory (parsed from the JSON weather grid provided at creation time). Each
 * tile request triggers an on-the-fly render of the corresponding geographic
 * area using the configured color ramp.
 *
 * <p>Architecture mirrors {@code HillshadingTileSource}: direct extension of
 * {@code TileSource}, with a companion {@code ITileDataSource} that does the
 * per-tile rendering work in {@link ITileDataSource#query}.
 */
public class WeatherTileSource extends TileSource {

    private final WeatherGridData gridData;
    private final String parameter;
    private final int timeIndex;
    private final String colorMap;

    /**
     * @param zoomMin   Minimum zoom level at which tiles are requested.
     * @param zoomMax   Maximum zoom level at which tiles are requested.
     * @param gridData  The parsed weather grid data (bounds, values, etc.).
     * @param parameter The parameter being displayed (e.g. "WIND", "TEMP").
     * @param timeIndex Which forecast time step to render.
     * @param colorMap  Identifier for the color ramp to apply.
     */
    public WeatherTileSource(
        int zoomMin,
        int zoomMax,
        WeatherGridData gridData,
        String parameter,
        int timeIndex,
        String colorMap
    ) {
        super(new Builder<>()
            .zoomMin(zoomMin)
            .zoomMax(zoomMax)
            .tileSize(256));
        this.gridData = gridData;
        this.parameter = parameter;
        this.timeIndex = timeIndex;
        this.colorMap = colorMap;
    }

    @Override
    public ITileDataSource getDataSource() {
        return new WeatherTileDataSource(this);
    }

    @Override
    public OpenResult open() {
        // Data is already in memory — nothing to open.
        return OpenResult.SUCCESS;
    }

    @Override
    public void close() {
        // Data is in memory — nothing to close.
    }

    // Package-private accessors for WeatherTileDataSource
    WeatherGridData getGridData() { return gridData; }
    String getParameter() { return parameter; }
    int getTimeIndex() { return timeIndex; }
    String getColorMap() { return colorMap; }
}
