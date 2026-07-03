/**
 * External dependencies
 */
import { useContext, useEffect } from 'react';

/**
 * Internal dependencies
 */
import WeatherOverlayModule, {
  type WeatherOverlayProps,
} from '../NativeModules/NativeWeatherOverlay';
import type { ErrorBase } from '../types';
import reportNativeError from '../reportNativeError';

/**
 * Peer dependencies (from react-native-mapsforge-vtm)
 */
import {
  MapHandleContext,
  useLayerOrder,
  useNativeLayerLifecycle,
} from 'react-native-mapsforge-vtm';

const WeatherOverlay = ({
  dataUrl,
  parameter,
  timeIndex,
  colorMap,
  opacity,
  zoomMin,
  zoomMax,
  enabledZoomMin,
  enabledZoomMax,
  onCreate,
  onRemove,
  onChange,
  onError,
}: WeatherOverlayProps) => {
  const { nativeNodeHandle } = useContext(MapHandleContext);

  const { uuid, triggerCreate, triggerRemove } = useNativeLayerLifecycle({
    enabled: !!nativeNodeHandle && !!dataUrl,
    create: ({ triggerOnCreate, triggerOnChange }) => {
      if (!nativeNodeHandle) {
        return Promise.reject<string>({
          userInfo: { errorMsg: 'Missing nativeNodeHandle' },
        } as ErrorBase);
      }
      return WeatherOverlayModule.createLayer({
        nativeNodeHandle,
        positionIndex,
        ...(dataUrl && { dataUrl }),
        ...(parameter && { parameter }),
        ...(timeIndex != null && { timeIndex: Math.round(timeIndex) }),
        ...(colorMap && { colorMap }),
        ...(opacity != null && { opacity }),
        ...(zoomMin != null && { zoomMin: Math.round(zoomMin) }),
        ...(zoomMax != null && { zoomMax: Math.round(zoomMax) }),
        ...(enabledZoomMin != null && {
          enabledZoomMin: Math.round(enabledZoomMin),
        }),
        ...(enabledZoomMax != null && {
          enabledZoomMax: Math.round(enabledZoomMax),
        }),
      }).then((newUuid) => {
        triggerOnCreate && onCreate
          ? onCreate({ nativeNodeHandle, uuid: newUuid })
          : null;
        triggerOnChange && onChange
          ? onChange({ nativeNodeHandle, uuid: newUuid })
          : null;
        return newUuid;
      });
    },
    remove: (currentUuid, { triggerOnRemove }) => {
      if (!nativeNodeHandle) {
        return Promise.resolve(false);
      }
      return WeatherOverlayModule.removeLayer({
        nativeNodeHandle,
        uuid: currentUuid,
      })
        .then((removedUuid) => {
          triggerOnRemove && onRemove
            ? onRemove({ nativeNodeHandle, uuid: removedUuid })
            : null;
          return true;
        })
        .catch((err: ErrorBase) => {
          reportNativeError(err, onError);
          return false;
        });
    },
    onError,
  });

  const { positionIndex } = useLayerOrder(uuid);

  // Update enabledZoomMin/enabledZoomMax in place.
  useEffect(() => {
    if (nativeNodeHandle && uuid) {
      WeatherOverlayModule.updateEnabledZoomMinMax({
        nativeNodeHandle,
        uuid,
        ...(enabledZoomMin != null && {
          enabledZoomMin: Math.round(enabledZoomMin),
        }),
        ...(enabledZoomMax != null && {
          enabledZoomMax: Math.round(enabledZoomMax),
        }),
      }).catch((err: ErrorBase) => {
        reportNativeError(err, onError);
      });
    }
  }, [enabledZoomMin, enabledZoomMax, nativeNodeHandle, uuid, onError]);

  // Update opacity in place.
  useEffect(() => {
    if (nativeNodeHandle && uuid && opacity != null) {
      WeatherOverlayModule.setOpacity({
        nativeNodeHandle,
        uuid,
        opacity,
      }).catch((err: ErrorBase) => {
        reportNativeError(err, onError);
      });
    }
  }, [opacity, nativeNodeHandle, uuid, onError]);

  // Update timeIndex in place.
  useEffect(() => {
    if (nativeNodeHandle && uuid && timeIndex != null) {
      WeatherOverlayModule.setTimeIndex({
        nativeNodeHandle,
        uuid,
        timeIndex: Math.round(timeIndex),
      }).catch((err: ErrorBase) => {
        reportNativeError(err, onError);
      });
    }
  }, [timeIndex, nativeNodeHandle, uuid, onError]);

  // Recreate on these prop changes (baked into native construction).
  useEffect(() => {
    triggerRemove({ triggerOnRemove: false }).then((success) => {
      if (success) {
        triggerCreate({
          triggerOnCreate: false,
          triggerOnChange: true,
        });
      }
    });
  }, [
    dataUrl,
    parameter,
    colorMap,
    zoomMin,
    zoomMax,
    triggerRemove,
    triggerCreate,
  ]);

  return null;
};

WeatherOverlay.defaults = WeatherOverlayModule.getConstants();

export default WeatherOverlay;
