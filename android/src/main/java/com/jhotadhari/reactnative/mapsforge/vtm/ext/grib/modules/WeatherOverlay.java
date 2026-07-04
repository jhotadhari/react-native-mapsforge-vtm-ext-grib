package com.jhotadhari.reactnative.mapsforge.vtm.ext.grib.modules;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

import com.jhotadhari.reactnative.mapsforge.vtm.ext.grib.NativeWeatherOverlaySpec;

import org.oscim.android.MapView;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;

import com.jhotadhari.reactnative.mapsforge.vtm.ext.grib.tiles.WeatherGridData;
import com.jhotadhari.reactnative.mapsforge.vtm.ext.grib.tiles.WeatherTileSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

// These are public classes in the parent library react-native-mapsforge-vtm
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.LayerHelper;
import com.jhotadhari.reactnative.mapsforge.vtm.LayerZoomBoundsHelper;

public class WeatherOverlay extends NativeWeatherOverlaySpec {

    private final LayerHelper layerHelper;
    private final LayerZoomBoundsHelper zoomBoundsHelper;

    // In-memory cache of parsed grid data, keyed by dataUrl + "|" + parameter + "|" + timeIndex.
    // LRU eviction with a reasonable cap — each grid is ~128 KB+, and unbounded
    // retention across URL/parameter/timestep combinations would cause OOM.
    private static final int MAX_GRID_CACHE_SIZE = 32;
    private final Map<String, WeatherGridData> gridCache = new LinkedHashMap<String, WeatherGridData>(
            16, 0.75f, true  // access-order for LRU
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, WeatherGridData> eldest) {
            return size() > MAX_GRID_CACHE_SIZE;
        }
    };

    public WeatherOverlay(ReactApplicationContext reactContext) {
        super(reactContext);
        layerHelper = new LayerHelper(this, reactContext);
        zoomBoundsHelper = new LayerZoomBoundsHelper(this, reactContext);
    }

    @NonNull
    @Override
    public Map<String, Object> getTypedExportedConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("dataUrl", "");
        constants.put("parameter", "WIND");
        constants.put("timeIndex", 0);
        constants.put("colorMap", "wind");
        constants.put("opacity", 0.7);
        constants.put("zoomMin", 1);
        constants.put("zoomMax", 18);
        constants.put("enabledZoomMin", 1);
        constants.put("enabledZoomMax", 18);
        return constants;
    }

    // ------------------------------------------------------------------
    // JS-facing methods (must match the TurboModule spec in JS)
    // ------------------------------------------------------------------

    @ReactMethod
    public void createLayer(ReadableMap params, Promise promise) {
        try {
            if (!Utils.rMapHasKey(params, "nativeNodeHandle")) {
                Utils.promiseReject(promise, "Undefined nativeNodeHandle");
                return;
            }

            MapView mapView = Utils.getMapView(
                getReactApplicationContext(),
                params.getInt("nativeNodeHandle")
            );
            if (null == mapView) {
                Utils.promiseReject(promise, "Unable to find mapView");
                return;
            }

            // Get params, assign defaults.
            Map<String, Object> defaults = getTypedExportedConstants();
            String dataUrl = Utils.rMapHasKey(params, "dataUrl")
                ? params.getString("dataUrl") : (String) defaults.get("dataUrl");
            String parameter = Utils.rMapHasKey(params, "parameter")
                ? params.getString("parameter") : (String) defaults.get("parameter");
            int timeIndex = Utils.rMapHasKey(params, "timeIndex")
                ? params.getInt("timeIndex") : (int) defaults.get("timeIndex");
            String colorMap = Utils.rMapHasKey(params, "colorMap")
                ? params.getString("colorMap") : (String) defaults.get("colorMap");
            double opacity = Utils.rMapHasKey(params, "opacity")
                ? params.getDouble("opacity") : (double) defaults.get("opacity");
            int zoomMin = Utils.rMapHasKey(params, "zoomMin")
                ? params.getInt("zoomMin") : (int) defaults.get("zoomMin");
            int zoomMax = Utils.rMapHasKey(params, "zoomMax")
                ? params.getInt("zoomMax") : (int) defaults.get("zoomMax");

            // Load grid data (cached in memory after first load).
            // Include timeIndex in the key so different timesteps of the
            // same URL+parameter are cached independently.
            String cacheKey = dataUrl + "|" + parameter + "|" + timeIndex;
            WeatherGridData gridData = gridCache.get(cacheKey);
            if (gridData == null) {
                gridData = fetchAndParseGridData(dataUrl, parameter, timeIndex);
                if (gridData != null) {
                    gridCache.put(cacheKey, gridData);
                }
            }

            if (gridData == null) {
                Utils.promiseReject(promise,
                    "Unable to load weather data from: " + dataUrl);
                return;
            }

            // Create the tile source (HillshadingTileSource pattern).
            WeatherTileSource tileSource = new WeatherTileSource(
                zoomMin, zoomMax, gridData, parameter, timeIndex, colorMap
            );

            // Create the bitmap tile layer.
            BitmapTileLayer layer = new BitmapTileLayer(
                mapView.map(), tileSource, (float) opacity
            );

            // Add to map via the async helper.
            layerHelper.addLayerAsync(layer, params)
                .thenAccept(uid -> {
                    // Clear the map to trigger tile loading for the new layer.
                    mapView.map().clearMap();
                    promise.resolve(uid);
                })
                .exceptionally(throwable -> {
                    Utils.promiseReject(promise,
                        "Unable to add layer: " + throwable.getMessage());
                    return null;
                });

        } catch (Exception e) {
            e.printStackTrace();
            Utils.promiseReject(promise, e.getMessage());
        }
    }

    @ReactMethod
    public void removeLayer(ReadableMap params, Promise promise) {
        layerHelper.removeLayerAsync(params)
            .thenRun(() -> {
                if (Utils.rMapHasKey(params, "uuid")) {
                    promise.resolve(params.getString("uuid"));
                } else {
                    promise.resolve("");
                }
            })
            .exceptionally(t -> {
                Utils.promiseReject(promise, t.getMessage());
                return null;
            });
    }

    @ReactMethod
    public void updateEnabledZoomMinMax(ReadableMap params, Promise promise) {
        try {
            if (!Utils.rMapHasKey(params, "uuid")
                || !Utils.rMapHasKey(params, "nativeNodeHandle")) {
                Utils.promiseReject(promise, "Undefined uuid or nativeNodeHandle");
                return;
            }
            zoomBoundsHelper.updateEnabledZoomMinMax(params, promise);
        } catch (Exception e) {
            e.printStackTrace();
            Utils.promiseReject(promise, e.getMessage());
        }
    }

    @ReactMethod
    public void setOpacity(ReadableMap params, Promise promise) {
        try {
            if (!Utils.rMapHasKey(params, "uuid")
                || !Utils.rMapHasKey(params, "nativeNodeHandle")) {
                Utils.promiseReject(promise, "Undefined uuid or nativeNodeHandle");
                return;
            }

            MapView mapView = Utils.getMapView(
                getReactApplicationContext(),
                params.getInt("nativeNodeHandle")
            );
            if (null == mapView) {
                Utils.promiseReject(promise, "Unable to find mapView");
                return;
            }

            double opacity = Utils.rMapHasKey(params, "opacity")
                ? params.getDouble("opacity")
                : (double) getTypedExportedConstants().get("opacity");

            // Route through the UI thread — BitmapTileLayer.setBitmapAlpha
            // triggers a map redraw and must not be called from the bridge
            // thread (consistent with the core library's threading model).
            com.facebook.react.bridge.UiThreadUtil.runOnUiThread(() -> {
                try {
                    BitmapTileLayer layer = (BitmapTileLayer) layerHelper
                        .getLayers(params.getInt("nativeNodeHandle"))
                        .get(params.getString("uuid"));

                    if (null == layer) {
                        Utils.promiseReject(promise, "Unable to find layer");
                        return;
                    }

                    layer.setBitmapAlpha((float) opacity, true);
                    promise.resolve(params.getString("uuid"));
                } catch (Exception e) {
                    Utils.promiseReject(promise, e.getMessage());
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            Utils.promiseReject(promise, e.getMessage());
        }
    }

    @ReactMethod
    public void setTimeIndex(ReadableMap params, Promise promise) {
        try {
            if (!Utils.rMapHasKey(params, "uuid")
                || !Utils.rMapHasKey(params, "nativeNodeHandle")) {
                Utils.promiseReject(promise, "Undefined uuid or nativeNodeHandle");
                return;
            }

            // Time index changes are handled by the JS side via the
            // remove+create pattern in WeatherOverlay's recreate useEffect
            // (timeIndex is in its dependency array). The recreate path
            // fetches fresh data with the correct cache key and creates a
            // new WeatherTileSource. This method exists for API symmetry
            // and future in-place interpolation support.
            promise.resolve(params.getString("uuid"));

        } catch (Exception e) {
            e.printStackTrace();
            Utils.promiseReject(promise, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Data loading
    // ------------------------------------------------------------------

    /**
     * Fetches a JSON weather grid from a URL and parses it.
     *
     * <p>Expected JSON format:
     * <pre>
     * {
     *   "parameter": "WIND",
     *   "unit": "m/s",
     *   "timeSteps": [{
     *     "validTime": "2026-07-03T00:00:00Z",
     *     "minLat": 48.0, "maxLat": 52.0,
     *     "minLng": -2.0, "maxLng": 3.0,
     *     "ni": 20, "nj": 16,
     *     "values": [0.5, 1.2, ...]
     *   }]
     * }
     * </pre>
     */
    private WeatherGridData fetchAndParseGridData(
        String dataUrl, String parameter, int timeIndex
    ) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(dataUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), "UTF-8")
            );
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject root = new JSONObject(sb.toString());

            String param = root.optString("parameter", parameter);
            String unit = root.optString("unit", "");

            JSONArray timeSteps = root.getJSONArray("timeSteps");
            if (timeIndex < 0 || timeIndex >= timeSteps.length()) {
                return null;
            }

            JSONObject step = timeSteps.getJSONObject(timeIndex);

            double minLat = step.getDouble("minLat");
            double maxLat = step.getDouble("maxLat");
            double minLng = step.getDouble("minLng");
            double maxLng = step.getDouble("maxLng");
            int ni = step.getInt("ni");
            int nj = step.getInt("nj");

            JSONArray valuesArr = step.getJSONArray("values");
            float[] values = new float[valuesArr.length()];
            for (int i = 0; i < valuesArr.length(); i++) {
                if (valuesArr.isNull(i)) {
                    values[i] = Float.NaN;
                } else {
                    values[i] = (float) valuesArr.getDouble(i);
                }
            }

            return new WeatherGridData(
                minLat, maxLat, minLng, maxLng, ni, nj, values, param, unit
            );

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
