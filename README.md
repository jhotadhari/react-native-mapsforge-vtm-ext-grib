# react-native-mapsforge-vtm-ext-grib

Weather GRIB overlay extension for [`react-native-mapsforge-vtm`](https://github.com/jhotadhari/react-native-mapsforge-vtm).
Renders gridded weather data (wind, temperature, pressure, precipitation, waves) as colored overlays on offline vector maps.

**Android only** — matches the platform scope of the parent library.

## Quick Start

```tsx
import { MapContainer, LayerMapsforge } from 'react-native-mapsforge-vtm';
import { WeatherOverlay } from 'react-native-mapsforge-vtm-ext-grib';

<MapContainer>
  <LayerMapsforge mapFile="/sdcard/maps/germany.map" />
  <WeatherOverlay
    dataUrl="https://weather.example.com/forecast.json"
    parameter="WIND"
    timeIndex={0}
    colorMap="wind"
    opacity={0.7}
  />
</MapContainer>
```

## Features (Phase 1 — Static Overlay)

- Colored grid overlay from pre-processed JSON weather data
- 6 built-in color ramps: `temperature`, `wind`, `pressure`, `rainfall`, `waves`, `grayscale`
- Per-pixel bilinear interpolation from the source grid
- Zoom-level bounds (`zoomMin`/`zoomMax`, `enabledZoomMin`/`enabledZoomMax`)
- Layer opacity control
- Time step selection

## JSON Data Format

The `dataUrl` should point to a JSON file with this structure:

```json
{
  "parameter": "WIND",
  "unit": "m/s",
  "timeSteps": [
    {
      "validTime": "2026-07-03T00:00:00Z",
      "minLat": 47.0,
      "maxLat": 55.0,
      "minLng": 5.0,
      "maxLng": 16.0,
      "ni": 44,
      "nj": 32,
      "values": [0.5, 1.2, null, 3.1, ...]
    }
  ]
}
```

- `values` is a row-major array of length `ni * nj`. `null` entries represent missing data (rendered as transparent).
- `ni`: number of grid points in the longitude direction.
- `nj`: number of grid points in the latitude direction.

Use [`@weacast/grib2json`](https://www.npmjs.com/package/@weacast/grib2json) for server-side GRIB→JSON conversion. On-device GRIB parsing (via JGribX) is planned for Phase 5.

## Installation

```sh
yarn add react-native-mapsforge-vtm-ext-grib
```

This library has a **peer dependency** on `react-native-mapsforge-vtm` and `react-native-reanimated`.

## Example app

```sh
# Clone and install:
git clone https://github.com/jhotadhari/react-native-mapsforge-vtm-ext-grib
cd react-native-mapsforge-vtm-ext-grib
yarn install

# Development (with yalc-linked react-native-mapsforge-vtm):
cd example && yalc link react-native-mapsforge-vtm && cd ..
yarn example android

# Serve sample weather data for the example:
cd example/data && python3 -m http.server 8000
```

## Documentation

- [Extending react-native-mapsforge-vtm](https://github.com/jhotadhari/react-native-mapsforge-vtm/blob/main/docs/advanced/extending.md) — extension architecture guide
- [ext-plan skill](https://github.com/jhotadhari/react-native-mapsforge-vtm/blob/main/.claude/skills/ext-plan.md) — interactive scaffolding command

## Architecture

`WeatherOverlay` creates a custom vtm `TileSource` (`WeatherTileSource`) that renders weather grid data into bitmap tiles on demand. The tile source is backed by a `BitmapTileLayer` — the same layer type used for OSM raster tiles and hillshading in the parent library.

```
WeatherGridData (in-memory)
    → WeatherTileSource (extends TileSource)
        → WeatherTileDataSource (implements ITileDataSource)
            → query() → bitmap → setTileImage()
                → BitmapTileLayer
```

## Roadmap

| Phase | Feature | Status |
|---|---|---|
| 1 | Static colored overlay (tile-based) | ✅ Current |
| 2 | Smooth time animation (dual-layer crossfade) | Planned |
| 3 | Custom vtm Layer (GPU interpolation + particles) | Planned |
| 4 | Wind barbs, isobars, cursor readout | Planned |
| 5 | On-device GRIB parsing (JGribX + jj2000) | Planned |
| 6 | Polish, performance, reanimated bearing/tilt | Planned |

## License

MIT
