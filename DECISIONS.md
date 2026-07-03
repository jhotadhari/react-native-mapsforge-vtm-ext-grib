# Open Decisions — `react-native-mapsforge-vtm-ext-grib`

These are the architectural choices that shape this library. Each section presents options
with concrete tradeoffs — open this file with Claude to get interactive guidance through
each decision.

---

## 1. Rendering approach for Phase 1

How should the first working version render weather data on the map?

### Option A: Tile-based (current implementation)

Extend vtm's `TileSource` — the same pattern used by `HillshadingTileSource` in the parent
library. `WeatherTileDataSource.query()` receives a `MapTile`, converts to lat/lon bounds,
samples the weather grid, renders a 256×256 `Bitmap`, and hands it to `BitmapTileLayer`.

| Pros | Cons |
|---|---|
| Proven pattern — `HillshadingTileSource` already uses it | Per-tile bitmap allocation: creates a 256×256 ARGB bitmap per tile request |
| Ships fast — 1-2 weeks to working overlay | Time animation requires regenerating tiles (not 60fps-smooth for interpolation, but can crossfade between pre-rendered tiles) |
| Zero OpenGL knowledge needed | Tile seams may be visible at some zoom levels |
| Tile caching "just works" via vtm's built-in SQLite cache | Pixel-perfect grid rendering is O(tileSize²) per tile |

### Option B: Custom vtm `Layer` subclass (skip tiles)

Extend `org.oscim.layers.Layer` directly — render the weather grid to OpenGL in the layer's
`update()`/`render()` methods. Upload the GRIB grid as a GPU texture, apply the color ramp
in the fragment shader.

| Pros | Cons |
|---|---|
| GPU-level interpolation between time steps — true 60fps animation | Steep learning curve — need to understand vtm's OpenGL pipeline, shader management, vertex buffers |
| No per-tile allocations — memory efficient | ~4-6 weeks to working overlay |
| Particles possible (advect by wind field in shader) | Coupled to vtm's rendering internals — breakage risk on vtm version upgrades |
| This is what vtm discussion #938 was trying to do | |

### Option C: Hybrid — ship tiles first, migrate to custom Layer later

Deliver Option A now, replace the backend with Option B in Phase 3. The JS API
(`<WeatherOverlay>`) stays identical — only the native implementation changes.

| Pros | Cons |
|---|---|
| Users get working weather overlays in weeks, not months | Some tile-source code is throwaway |
| Validates the whole pipeline (data format, color ramps, React API) before investing in OpenGL | Need to maintain the tile code until the custom Layer is stable |
| The custom Layer can be developed against a known-good API and data format | |

**Relevant context:**
- vtm discussion #938 asked exactly "how to create an OpenGL matrix for GRIB rendering" and went unanswered
- vtm's `MapPosition` class provides the Mercator projection matrix — a custom `Layer` subclass gets it for free in `render()`
- The parent library's `LayerHillshading.java` is a working reference for Option A

---

## 2. GRIB data source

Where does the weather data come from?

### Option A: Server-side GRIB→JSON (current implementation)

A backend service converts GRIB files to JSON grids. The mobile app fetches JSON via HTTP.

| Pros | Cons |
|---|---|
| Ships immediately — zero GRIB parsing on mobile | Requires network connectivity |
| Works on any Android version (no JDK 26+ requirement) | Server needs to run, maintain, and pay for |
| Small APK — no GRIB parsing library bundled | User can't load their own GRIB files |
| Can pre-process (subset, interpolate, re-project) before sending to mobile | |

### Option B: On-device GRIB parsing (JGribX + jj2000)

Bundle a pure-Java GRIB parser in the Android library. Parse `.grb2` files directly on the
device.

| Pros | Cons |
|---|---|
| Works offline — essential for marine/sailing use | JGribX GRIB2 JPEG2000 support is unverified — may need forking |
| User can load any GRIB file (SailDocs, NOAA, custom) | Adds ~1.3MB to APK (JGribX ~500KB + jj2000 ~800KB) |
| No server infrastructure needed | GRIB is complex: need to handle GRIB1 + GRIB2, multiple templates, multiple packing methods |

### Option C: Both — server for quick start, on-device as fallback

Support both: fetch from URL if available, parse local GRIB files for offline use.

**Key constraint:** The JSON grid format (`WeatherGridData`) is identical regardless of
source. The rendering layers don't care where the data came from.

**Relevant context:**
- NOAA GFS (the most common free GRIB source) uses JPEG2000-compressed GRIB2
- `edu.ucar:jj2000` is the only pure-Java JPEG2000 decoder suitable for Android (~800KB)
- netCDF-Java requires minSdkVersion 26 — cuts off ~10-15% of Android devices
- ecCodes Java bindings are not feasible on Android (native .so cross-compilation per ABI, 20-50MB per ABI)

---

## 3. Smooth animation approach

The user explicitly asked for "smooth animateable." How to achieve this?

### Option A: Dual-layer crossfade (Phase 2, with tiles)

Maintain two `BitmapTileLayer` instances (frame N, frame N+1). Crossfade between them by
animating opacity via a reanimated `SharedValue`. Tile layer opacity is a single uniform
write in vtm's OpenGL pipeline — zero tile regeneration after the first render.

| Pros | Cons |
|---|---|
| 60fps with zero per-frame work (pure GPU blend) | Only animates between pre-existing frames — no continuous interpolation of the data itself |
| Works with the tile-based Phase 1 — ship in ~4 weeks total | Each new time step pair triggers tile regeneration (delayed blur during scrubbing) |
| Reanimated hook already written (`useWeatherAnimation.ts`) | Can't interpolate between more than 2 frames simultaneously |

### Option B: GPU shader interpolation (Phase 3, with custom Layer)

Upload two GRIB grids as GPU textures, interpolate in the fragment shader:
`mix(gridCurrent, gridNext, t)`. The color ramp is also applied in the shader.

| Pros | Cons |
|---|---|
| True continuous interpolation — any fractional time step, not just binary crossfade | Requires custom vtm Layer (Option B from decision 1) |
| Wind particles can use the same interpolated wind field | ~6-8 weeks from start |
| No tile regeneration — instant scrubbing through the forecast | |

### Option C: Pre-rendered tile frames + reanimated progress

Pre-generate tiles for ALL time steps. Animate a single shared value that selects which
tile set to display.

| Pros | Cons |
|---|---|
| Simple — just changes which TileSource is active | Memory: N time steps × M tiles = potentially hundreds of bitmaps |
| Can scrub freely | Still discrete (no interpolation between frames) |
| | Tile pre-generation is slow for large forecasts |

---

## 4. Wind particles

Should we render animated wind particles (like OpenCPN's particle map)?

### Option A: Yes, as a dedicated native layer (Phase 3)

`WeatherParticleLayer extends org.oscim.layers.Layer`:
- Pool of 1000–5000 particles, adaptive to zoom
- Each frame: advect by wind (U,V) from grid, wrap at edges, cull off-screen
- Render as `GL_POINTS` (dots) or `GL_LINES` (trail lines), colored by speed

### Option B: Yes, as reanimated React Native views

Create Animated.Views positioned via the parent library's `useMapOverlay()` worklet pattern.
Each particle is a tiny `<View>` with absolute positioning.

| Pros | Cons |
|---|---|
| Pure JS — no native rendering code | Limited to ~500 particles before frame drops (each is a React Native view) |
| Can use reanimated's animation primitives (withTiming, etc.) | No trail rendering |
| | vs. OpenCPN's thousands of GPU particles |

### Option C: No particles for MVP

Ship the color overlay first. Add particles as a separate component later.

---

## 5. Library scope

What should this library do vs. what should be left to the consuming app?

### Option A: Rendering only

The library handles: GRIB parsing (or JSON fetching), color mapping, tile rendering,
animation. The app is responsible for: downloading GRIB files, managing file storage,
providing UI controls.

### Option B: Rendering + data acquisition

Add downloader components (`<WeatherDownloader>`) that fetch GRIB files from SailDocs,
NOAA NOMADS, or custom URLs. Include file management (cache, auto-delete old forecasts).

### Option C: Rendering + data + UI

Ship a complete weather overlay UI kit: playback controls, parameter picker, color map
selector, cursor data readout panel.

---

## 6. Library name and npm convention

Current name: `react-native-mapsforge-vtm-ext-grib`

### Option A: Keep current name

`react-native-mapsforge-vtm-ext-grib` — follows the `react-native-` community convention,
clearly indicates the parent library.

| Pros | Cons |
|---|---|
| Discoverable on npm | Very long (43 characters) |
| Clear relationship to parent library | "ext" is a naming convention we invented |

### Option B: Drop the `react-native-` prefix

`mapsforge-vtm-ext-grib` — shorter, the `react-native.config.js` handles autolinking
regardless of prefix.

### Option C: Scoped package

`@jhotadhari/mapsforge-vtm-ext-grib` or `@jhotadhari/vtm-weather` — avoids npm name
collisions, allows a family of packages.

### Option D: Shorter name

`vtm-weather-overlay` — concise, descriptive, not tied to GRIB (supports other weather
data formats later).

---

## 7. Data format: JSON schema design

What should the JSON weather grid format look like?

### Current format

```json
{
  "parameter": "WIND",
  "unit": "m/s",
  "timeSteps": [{
    "validTime": "2026-07-03T00:00:00Z",
    "minLat": 48.0, "maxLat": 54.0,
    "minLng": 6.0, "maxLng": 14.0,
    "ni": 32, "nj": 24,
    "values": [0.5, 1.2, null, 3.1, ...]
  }]
}
```

### Open questions

- **Single parameter vs. multi-parameter?** Current format has one parameter per file.
  Real GRIB files contain multiple parameters (wind, temp, pressure) in one file. Should
  the JSON bundle all parameters together?

- **Vector components?** Wind has U (eastward) and V (northward) components. The current
  format only stores speed. Barbs and particles need direction too. Should `values` be an
  array of `{ u, v }` objects, or separate `valuesU`/`valuesV` arrays?

- **Grid type?** Current format assumes a regular lat/lon grid. GRIB also supports
  Gaussian grids, Lambert conformal, polar stereographic. Should we support projections,
  or require pre-projection to regular lat/lon?

- **Binary vs. JSON?** For a 0.25° global grid (1440×721 = ~1M points), JSON is ~15MB
  uncompressed. Binary (Float32Array) would be ~4MB. Should we support both?

---

## 8. Extension point pattern in the parent library

The parent library (`react-native-mapsforge-vtm`) now exports three hooks that this
extension uses:

- `MapHandleContext` — React context for `nativeNodeHandle` + `LayerOrderRegistry`
- `useLayerOrder(uuid)` — registers position in render tree
- `useNativeLayerLifecycle({ enabled, create, remove })` — `null → false → uuid` state machine

### Open questions

- **Are these the right three?** Are there other parent-library internals that a
  weather extension (or any future extension) needs?

- **Reexport from this library?** Should `react-native-mapsforge-vtm-ext-grib` re-export
  these hooks so extension-of-extension libraries don't need a direct dependency on the
  parent?

- **Version coupling?** How tightly should this library pin its
  `react-native-mapsforge-vtm` peer dependency? `"*"` (current) vs. `">=0.7.0"` (requires
  the version that added these exports)?

---

## 9. Example app structure

What should the `example/` workspace look like?

### Option A: Minimal — mirror the parent library's pattern

A bare React Native app (like `react-native-mapsforge-vtm/example/`) with Android project
files, a single `App.tsx` that shows `MapContainer` + `WeatherOverlay`, and a sample data
file bundled or served locally.

### Option B: Full demo — map + weather + controls

A richer example with: multiple map layers, playback controls for time animation,
parameter switching UI, color map picker, data downloader.

### Option C: Standalone — no dependency on the parent library's example

The example app is self-contained with its own maps and data. Doesn't require the parent
library's example to be running.

---

## 10. Testing strategy

How should we verify this library works?

### Option A: Device-only testing (user does it)

The user tests on a physical device or emulator. The library provides sample data and
clear instructions. No automated tests for the rendering pipeline.

### Option B: Snapshot tests for color ramps and grid interpolation

Unit tests for `WeatherGridData.getValueAt()` (bilinear interpolation), `ColorRamp.getColor()`
(color mapping), and JSON parsing. No rendering tests.

### Option C: Instrumented Android tests

JUnit tests running on an Android device/emulator that verify: tile rendering (compare
bitmap output to expected), layer creation, zoom bounds, opacity changes.

---

## How to use this document

Open it with Claude and ask about any decision:

> "Walk me through decision 1 — I want to understand the tradeoffs between tiles and
> custom Layer for my use case."

> "For decision 2, my users are sailors who need offline data. Which option is best?"

> "I'm leaning toward Option C for decision 3. What am I missing?"

Claude will explain the options interactively, ask clarifying questions about your
specific context, and help you reach a decision. Decisions can be recorded here by
replacing the options with the chosen approach and a brief rationale.
