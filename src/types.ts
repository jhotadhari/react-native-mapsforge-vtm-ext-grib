/**
 * Base types shared across the ext-grib library.
 *
 * Mirrors the pattern from react-native-mapsforge-vtm's types.ts.
 */

export interface ResponseBase {
  uuid: string;
  nativeNodeHandle: number;
}

export interface ErrorBase {
  userInfo?: {
    errorMsg?: string;
  };
}

/**
 * Represents one time step of gridded weather data.
 * This is the format produced by server-side GRIB to JSON conversion
 * (and later by on-device JGribX parsing).
 */
export interface WeatherGrid {
  /** UTC ISO timestamp e.g. "2026-07-03T00:00:00Z" */
  validTime: string;
  /** Minimum latitude of the grid (southern edge) */
  minLat: number;
  /** Maximum latitude of the grid (northern edge) */
  maxLat: number;
  /** Minimum longitude of the grid (western edge) */
  minLng: number;
  /** Maximum longitude of the grid (eastern edge) */
  maxLng: number;
  /** Number of grid points in the longitude direction */
  ni: number;
  /** Number of grid points in the latitude direction */
  nj: number;
  /** Row-major grid values, length = ni * nj. Missing values are NaN. */
  values: number[];
}

/**
 * Metadata for a weather data source (returned after loading).
 */
export interface WeatherMetadata {
  parameter: string;
  levelType?: string;
  unit: string;
  timeSteps: WeatherGrid[];
}

/**
 * Built-in color ramp identifiers.
 */
export type ColorMapName =
  | 'temperature'
  | 'wind'
  | 'pressure'
  | 'rainfall'
  | 'waves'
  | 'grayscale';
