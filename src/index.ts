/**
 * react-native-mapsforge-vtm-ext-grib
 *
 * Weather GRIB overlay extension for react-native-mapsforge-vtm.
 */

// Main component
export { default as WeatherOverlay } from './components/WeatherOverlay';
export type { WeatherOverlayProps } from './NativeModules/NativeWeatherOverlay';

// Shared types
export type {
  WeatherGrid,
  WeatherMetadata,
  ColorMapName,
  ResponseBase,
  ErrorBase,
} from './types';

// Reanimated utilities (also available via /reanimated subpath export)
export {
  useWeatherAnimation,
} from './reanimated/useWeatherAnimation';
export type { WeatherAnimationControls } from './reanimated/useWeatherAnimation';
