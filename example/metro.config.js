const path = require('path');
const { getDefaultConfig } = require('@react-native/metro-config');

const root = path.resolve(__dirname, '..');
const parentRoot = path.resolve(
  __dirname,
  '../../react-native-mapsforge-vtm'
);

/**
 * Metro configuration
 * https://facebook.github.io/metro/docs/configuration
 *
 * @type {import('metro-config').MetroConfig}
 */
const config = getDefaultConfig(__dirname);

module.exports = {
  ...config,
  watchFolders: [...(config.watchFolders ?? []), root, parentRoot],
  resolver: {
    ...config.resolver,
    extraNodeModules: {
      ...(config.resolver?.extraNodeModules ?? {}),
      'react-native-mapsforge-vtm-ext-grib': root,
      'react-native-mapsforge-vtm': parentRoot,
    },
  },
};
