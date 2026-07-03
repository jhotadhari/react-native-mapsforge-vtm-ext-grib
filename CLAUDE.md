# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`react-native-mapsforge-vtm-ext-grib` is a React Native library that adds weather GRIB overlay
capability on top of `react-native-mapsforge-vtm`. It renders gridded weather data (wind,
temperature, pressure, precipitation, waves) as colored overlays on offline vector maps.
**Android only** — matches the platform scope of the parent library.

The parent library lives at `../react-native-mapsforge-vtm` (sibling directory). This extension
was built as the first external consumer of the parent library's layer-type extension points —
`MapHandleContext`, `useLayerOrder`, and `useNativeLayerLifecycle` — which were exported
specifically to enable this library.

## Common commands

This is a Yarn workspaces package (`packageManager: yarn@3.6.1`). An `example/` workspace will
be added for manual testing (not yet present — see Phase 2 planning in ROADMAP.md).

```sh
yarn                  # install deps (uses yarn workspaces)
yarn typecheck        # tsc — no emit, just checks
yarn lint             # eslint over **/*.{js,ts,tsx} (flat config, eslint.config.mjs)
yarn format           # prettier . --write
yarn clean            # del-cli android/build lib
yarn prepare          # bob build — builds lib/ (codegen + module + typescript) from src/
```

`lefthook.yml` runs `eslint` and `tsc` on staged `*.{js,ts,jsx,tsx}` files as a pre-commit hook.

## Architecture

### Extension pattern — three hooks from the parent library

This library does **not** duplicate the parent's layer infrastructure. It imports three hooks
that the parent library exports as stable extension points:

| Hook | Source | What it provides |
|---|---|---|
| `MapHandleContext` | `react-native-mapsforge-vtm` | React context with `nativeNodeHandle` (map view ID) and `LayerOrderRegistry` |
| `useLayerOrder(uuid)` | `react-native-mapsforge-vtm` | Registers the component's position in render order, returns `{ nativeNodeHandle, positionIndex, fragmentUuid }` |
| `useNativeLayerLifecycle({ enabled, create, remove })` | `react-native-mapsforge-vtm` | Manages `null → false → uuid` state machine; callers only provide `create`/`remove` callbacks that return Promises |

Any future layer-type extension (traffic, thermal, radar) would use exactly these same three hooks.
The pattern is: `MapHandleContext` for map identity, `useLayerOrder` for z-ordering,
`useNativeLayerLifecycle` for create/remove lifecycle.

### Data flow

```
JS (React)                              Native (Java)
──────────                              ──────────────
<WeatherOverlay
  dataUrl="https://..."           →     WeatherOverlay.java
  parameter="WIND"                       ├─ fetch JSON via HTTP
  timeIndex={0}                          ├─ parse to WeatherGridData
  colorMap="wind"                        ├─ create WeatherTileSource
  opacity={0.7}                          ├─ wrap in BitmapTileLayer
/>                                       └─ addLayerAsync(layer) → MapMutationQueue
```

### Native side — custom TileSource (not a custom Layer)

Phase 1 uses the **TileSource pattern** (proven by `HillshadingTileSource` in the parent library):

```
WeatherTileSource extends TileSource
  └─ getDataSource() → WeatherTileDataSource implements ITileDataSource
       └─ query(MapTile tile, ITileDataSink sink)
            ├─ tileToBoundingBox(tile.tileX, tile.tileY, tile.zoomLevel) → lat/lon bounds
            ├─ grid.getValueAt(lat, lng) per pixel → bilinear interpolation
            ├─ ColorRamp.getColor(value) → ARGB int
            ├─ Bitmap.setPixels() → sink.setTileImage(bitmap)
            └─ sink.completed(QueryResult.SUCCESS)
```

`WeatherTileDataSource.query()` is called by vtm's tile manager on a **background thread**.
Bitmap rendering is thread-safe because it only reads `WeatherGridData`, which is immutable
after construction. No synchronization needed.

**Phase 3 (planned) replaces this with `WeatherGridLayer extends org.oscim.layers.Layer`**
for GPU-level interpolation and particle animation. The JS API (`<WeatherOverlay>`)
stays identical — only the native backend changes.

### Threading

This library inherits the parent's threading model:
- `MapMutationQueue.flush()` runs on the **UI thread** (Main Looper) — the only place that
  calls `layers().add/remove` and batch-level `updateMap()`
- `WeatherTileDataSource.query()` runs on vtm's **tile worker thread** (background)
- `WeatherOverlay.java` module methods run on the **native modules thread** (TurboModule)
- `fetchAndParseGridData()` runs on the native modules thread — blocks until HTTP completes
  (acceptable for initial load; should move to a background thread for large files)

### Java package layout

```
android/src/main/java/com/jhotadhari/reactnative/mapsforge/vtm/ext/grib/
  ExtGribPackage.java           — ReactPackage, registers WeatherOverlay module
  modules/
    WeatherOverlay.java         — TurboModule: createLayer, removeLayer, setOpacity, etc.
  tiles/
    WeatherTileSource.java      — extends TileSource (direct, not UrlTileSource)
    WeatherTileDataSource.java  — implements ITileDataSource, per-tile bitmap render
    WeatherGridData.java        — immutable in-memory grid with bilinear getValueAt()
    ColorRamp.java              — 6 built-in meteorological color ramps
```

### Codegen considerations

`src/NativeModules/NativeWeatherOverlay.ts` is the codegen spec. Types used in the `Spec`
interface **must be declared inline** — react-native-codegen's TypeScript parser cannot follow
imports. The `WeatherOverlayProps` type (used by the React component, not by the spec) can use
imported types.

After running `yarn prepare` (bob build), codegen generates
`android/generated/java/.../NativeWeatherOverlaySpec.java`. The hand-written
`WeatherOverlay.java` currently extends `ReactContextBaseJavaModule` directly with `@ReactMethod`
annotations. After codegen runs, it should be switched to extend `NativeWeatherOverlaySpec`
(the generated base class) — matching how the parent library's modules extend their generated
specs.

## Parent library gotchas

These were discovered while debugging the parent library's reanimated overlay example.
They apply to this extension if it uses `useMapOverlay` / `toScreenPosition` for spatial
overlays (weather icons, cursor readout, wind barbs as markers).

### responseInclude for the legacy channel

The parent's `useMapPosition()` returns `responseInclude: { center: 2, zoomLevel: 2, bearing: 2, tilt: 2, viewportWidth: 2, viewportHeight: 2 }`.
Spread this into `<MapContainer responseInclude={...} />` if you also wire the legacy
`onMapUpdate` channel. The fast channel (`onMapPosition`) sends all fields unconditionally
and doesn't need `responseInclude`.

### Fast channel (onMapPosition) for 60fps overlay tracking

The parent library provides a dual-channel position-event system:

| Channel | Event | Rate | Payload | Use case |
|---|---|---|---|---|
| **Fast** | `onMapPosition` | Every vtm frame (~60fps) | 8 flat doubles (lng, lat, zoom, zoomLevel, bearing, tilt, vpW, vpH) | Overlay positioning via `useMapPosition().handleMapPosition` |
| **Legacy** | `onMapUpdate` | Throttled at `mapUpdateInterval` | Full `MapEventResponse` with elevation | Debug displays, logging, non-reanimated consumers |

The fast channel fires unconditionally on every vtm frame — no rate limiter, no
`responseInclude` gating, no elevation disk I/O. The JS handler writes directly to
reanimated shared values, achieving true 60fps overlay tracking.

Usage:
```tsx
const pos = useMapPosition();
<MapContainer onMapPosition={pos.handleMapPosition} mapUpdateInterval={16}>
```

This extension inherits the fast channel automatically when using the parent's
`useMapPosition()` — no code changes needed in ext-grib to benefit from 60fps tracking.

### mapUpdateInterval still matters

The legacy `onMapUpdate` channel is still throttled at `mapUpdateInterval` (default 40ms).
Pass `mapUpdateInterval={16}` to keep the legacy channel responsive for debug displays
and non-reanimated consumers. The fast channel ignores this setting entirely.

### Fractional zoom — use getZoom(), not getZoomLevel()

vtm's `MapPosition.getZoomLevel()` returns `int` — truncated during pinch-zoom.
The parent library's `MapFragment` was fixed to use `getZoom()` (returns `double`)
so reanimated overlays track smoothly through fractional zoom levels (e.g. 2.7).
This is a native-side fix — no JS changes needed, and this extension inherits it
automatically since it uses the parent's `useMapPosition`.

## Key constraints

- **No real iOS implementation.** The `ios/` stub exists because codegen requires it, but
  there is no iOS rendering backend. All native code is under `android/`.
- **JSON data format only for Phase 1.** On-device GRIB parsing (JGribX + jj2000) is Phase 5.
- **The parent library must be on a branch that has the extension-point exports**
  (`MapHandleContext`, `useLayerOrder`, `useNativeLayerLifecycle`) and the fractional-zoom
  fix (`getZoom()` instead of `getZoomLevel()` in `MapFragment.java`).
- `react-native-reanimated` and `react-native-worklets` are optional peer dependencies
  (used only by `src/reanimated/useWeatherAnimation.ts`).

## What's not here yet

See `ROADMAP.md` for the full plan. The biggest gaps:
- No `example/` workspace app (Phase 2)
- Time animation uses the reanimated hook but doesn't wire it to dual-layer crossfade (Phase 2)
- No custom vtm `Layer` subclass — tile-based only (Phase 3)
- No wind particles, barbs, contours, or cursor readout (Phase 4)
- No on-device GRIB parsing — server-side JSON only (Phase 5)
- `WeatherOverlay.java` extends `ReactContextBaseJavaModule` directly — needs switch to
  generated `NativeWeatherOverlaySpec` after codegen runs
