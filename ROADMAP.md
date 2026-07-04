# Roadmap: `react-native-mapsforge-vtm-ext-grib`

Weather GRIB overlay extension for `react-native-mapsforge-vtm`. Renders gridded weather data
(wind, temperature, pressure, precipitation, waves) as colored overlays on offline vector maps.

## Architecture Overview

```
<MapContainer>
  <WeatherOverlay
    dataUrl="https://..."     ← JSON weather grid (server-side GRIB→JSON)
    parameter="WIND"
    timeIndex={0}
    colorMap="wind"
    opacity={0.7}
  />
</MapContainer>
        │
        ▼
  WeatherOverlay.tsx          ← React component (useNativeLayerLifecycle pattern)
        │
        ▼
  WeatherOverlay.java         ← TurboModule: fetch JSON, parse to WeatherGridData
        │
        ▼
  WeatherTileSource           ← extends TileSource (HillshadingTileSource pattern)
        │
        ▼
  WeatherTileDataSource       ← ITileDataSource.query() → render Bitmap per tile
        │
        ▼
  BitmapTileLayer             ← vtm's standard bitmap tile layer
```

### Threading Model

All layer mutations flow through the main library's `MapMutationQueue.flush()` on the UI
thread — the extension doesn't need its own threading. `WeatherTileDataSource.query()` is
called by vtm's tile manager on a background thread; bitmap rendering is thread-safe as
long as it only reads `WeatherGridData` (immutable after construction).

---

## Phases

### Phase 1 — Static Overlay (Tile-Based) ✅ Current

**Goal:** Parse a JSON weather grid and display one parameter as a colored overlay.

**Status:** Implemented in this repo. Core files:

| File | What |
|---|---|
| `src/NativeModules/NativeWeatherOverlay.ts` | TurboModule spec (codegen-ready, inline types) |
| `src/components/WeatherOverlay.tsx` | React component |
| `src/types.ts` | `WeatherGrid`, `WeatherMetadata`, `ColorMapName` |
| `src/reanimated/useWeatherAnimation.ts` | 60fps time-step crossfade animation |
| `android/.../tiles/WeatherTileSource.java` | Custom `TileSource` for weather data |
| `android/.../tiles/WeatherTileDataSource.java` | Per-tile bitmap renderer |
| `android/.../tiles/WeatherGridData.java` | In-memory grid with bilinear lookup |
| `android/.../tiles/ColorRamp.java` | 6 built-in meteorological color ramps |
| `android/.../modules/WeatherOverlay.java` | TurboModule: fetch JSON, create layer |

**Data format:**
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

Use `@weacast/grib2json` for server-side GRIB→JSON conversion.

### Phase 2 — Smooth Time Animation (Crossfade)

**Goal:** Animate between GRIB forecast time steps at 60fps.

**Approach:** Two `BitmapTileLayer` instances (frame N, frame N+1) at different opacity,
driven by a reanimated `SharedValue`. Changing tile layer opacity is a single uniform
write in vtm's OpenGL pipeline — zero tile regeneration, pure GPU work. Only the first
crossfade triggers any work; subsequent frames are cached.

**Key insight:** Smooth time animation doesn't require GPU shader interpolation. Two
pre-rendered tile layers with reanimated opacity crossfade at 60fps. The tile layers are
already in vtm's OpenGL pipeline, so changing opacity costs a single uniform write.

**Files to create:**
- Update `WeatherOverlay.tsx` to manage dual-layer internal state
- Update `WeatherOverlay.java` to support dual-layer creation
- `src/reanimated/useWeatherAnimation.ts` already exists — hook into it

**Parent library requirements (discovered during overlay debugging):**
- `<MapContainer mapUpdateInterval={16} />` — reduces the native event throttle from 40ms
  (~25fps) to 16ms (~60fps) so spatial overlays track smoothly during pan/zoom
- `<MapContainer responseInclude={pos.responseInclude} />` — must spread the full
  `useMapPosition().responseInclude` (includes `center: 2`, `zoomLevel: 2`,
  `viewportWidth: 2`, `viewportHeight: 2`), not just viewport dimensions
- The parent library's `MapFragment.java` must use `getZoom()` (fractional double)
  instead of `getZoomLevel()` (truncated int) — fixed in parent as of 2026-07-03

### Phase 3 — Custom vtm Layer (GPU Interpolation + Particles)

**Goal:** Replace tile-based backend with a custom vtm `Layer` subclass for GPU-level
interpolation and particle animation.

**`WeatherGridLayer extends org.oscim.layers.Layer`:**
- Accepts two GRIB grids (current + next frame) and an interpolation progress float
- Uploads grids as GPU textures
- Time interpolation in the fragment shader: `mix(gridCurrent, gridNext, t)`
- Color ramp in the shader (no per-tile bitmap allocation)
- At low zoom: renders a decimated grid

**`WeatherParticleLayer extends org.oscim.layers.Layer`:**
- Maintains particle pool (1000–5000, adaptive to zoom)
- Each frame: advect particles by wind (U,V) field from the grid
- Render as `GL_POINTS` (dots) or `GL_LINES` (trails), colored by speed
- Particles that leave the viewport are recycled

**Shader-level interpolation:**
```glsl
// Fragment shader
uniform sampler2D u_gridCurrent;
uniform sampler2D u_gridNext;
uniform float u_interp;

float value = mix(
  texture2D(u_gridCurrent, v_texCoord).r,
  texture2D(u_gridNext, v_texCoord).r,
  u_interp
);
gl_FragColor = colorMap(value);
```

This is what vtm discussion #938 was trying to achieve. The coordinate transform they got
stuck on is what vtm already provides via its Mercator projection matrix — a custom
`Layer` subclass gets it for free.

### Phase 4 — Advanced Visualizations

**Goal:** Wind barbs, isobars, cursor readout, multi-parameter stacking.

- **Wind barbs** (`<WindBarbs>`) — render traditional meteorological barbs at grid points.
  Could use `LayerMarker` with custom symbols (low density) or custom vector rendering
  (high density).

- **Contours / Isobars** (`<WeatherContours>`) — marching squares algorithm to extract
  contour lines from the grid, render via `LayerPathJts`.

- **Cursor data readout** (`<WeatherCursor>`) — tap on map → query all available
  parameters at that lat/lon. Uses the existing map `onTap` event + coordinate-to-grid
  lookup on the native side.

- **Multi-parameter stacking** — particles (wind) + contours (pressure) + color overlay
  (temperature). Each is a separate native layer, stackable via `LayerOrderRegistry`.

### Phase 5 — On-Device GRIB Parsing

**Goal:** Parse GRIB1/GRIB2 files directly on the device for offline use.

**Libraries:**
- **JGribX** (pure Java, MIT, ~500KB) — best lightweight option for GRIB1/2
- **edu.ucar:jj2000** (pure Java, ~800KB) — JPEG2000 decoder needed for GRIB2 files with
  JPEG2000-compressed sections
- Fallback: server-side conversion via `@weacast/grib2json` for apps that can rely on
  network

**Files to create:**
- `android/.../grib/GribReader.java` — JGribX wrapper, produces `WeatherGridData[]`
- Update `WeatherOverlay.java` to accept local file paths + URL fallback

**The rendering layers don't change** — they already consume `WeatherGridData`. This
phase only adds an alternative data source.

### Phase 6 — Polish & Performance

**Goal:** Production-quality finish.

- **Performance:** grid LOD at low zoom, adaptive particle count by zoom + device tier
- **Reanimated bearing/tilt:** extend `mercatorUtils.ts` in the main library to handle
  rotated/projected maps — benefits both libraries. **Done 2026-07-04 in parent library
  `feature/reanimated-overlay-projection` branch.** `toScreenPosition` and `fromScreenPosition`
  now accept `bearingSv` and `tiltSv` shared values and apply rotation + orthographic tilt
  foreshortening in worklets. Overlays track correctly on rotated and tilted maps.
- **File management:** download, cache, auto-delete old forecasts
- **Documentation:** full README with examples, publish to npm

---

## Timeline

| Milestone | What Ships | Estimated |
|---|---|---|
| **MVP** (Phases 1–2) | Static colored overlay + smooth time animation + playback controls | 3–5 weeks |
| **v2** (Phase 3) | Custom GPU layer + wind particles | +3–4 weeks |
| **v3** (Phases 4–5) | Barbs, isobars, cursor, offline parsing | +4–6 weeks |
| **Full** (Phase 6) | Polish, performance, docs | +1 week |
| **Total** | | **11–16 weeks** |

---

## Technical References

### vtm TileSource Hierarchy

```
TileSource (abstract)
 ├── UrlTileSource (abstract) → BitmapTileSource (URL-based tiles)
 ├── HillshadingTileSource (DEM → bitmap tiles) ← OUR PATTERN
 └── WeatherTileSource (GRIB → bitmap tiles)     ← IMPLEMENTED
```

The `ITileDataSource` interface:
```java
public interface ITileDataSource {
    void query(MapTile tile, ITileDataSink dataSink);
    void dispose();
    void cancel();
}
```

`query()` calls `dataSink.setTileImage(bitmap)` then `dataSink.completed(SUCCESS)`.

### GRIB Parsing Options

| Library | GRIB1 | GRIB2 | JPEG2000 | Android | Size |
|---|---|---|---|---|---|
| **JGribX** | Yes | Partial | Unknown | ✅ Pure Java | ~500KB |
| **netCDF-Java** | Yes | Full | Yes | ⚠️ SDK 26+ | 10+MB |
| **ecCodes Java** | Full | Full | Yes | ❌ JNI cross-compile | 20-50MB/ABI |
| **edu.ucar:jj2000** | N/A | JPEG2K only | Yes | ✅ Pure Java | ~800KB |

**Recommendation:** JGribX + jj2000 for on-device parsing. Validate against your GRIB
data source first (NOAA GFS uses JPEG2000 compression for GRIB2).

### OpenCPN GRIB Plugin (Inspiration)

OpenCPN's GRIB viewer supports: wind barbs, colored overlay maps, isobars/isotachs,
number grids, particle maps, cursor data readout, dual-file loading, time animation with
playback controls, and email-based download from SailDocs.

### Relevant Links

- [vtm discussion #938](https://github.com/mapsforge/vtm/discussions/938) — OpenGL ES
  GRIB rendering (unresolved — our custom Layer approach solves this)
- [OpenCPN GRIB Plugin](https://opencpn-manuals.github.io/main/grib/index.html) —
  reference implementation for features
- [@weacast/grib2json](https://www.npmjs.com/package/@weacast/grib2json) — server-side
  GRIB→JSON converter
- [JGribX](https://github.com/spidru/JGribX) — lightweight pure-Java GRIB parser
