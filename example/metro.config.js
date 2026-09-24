const path = require('path');
const { getDefaultConfig } = require('@react-native/metro-config');

const root = path.resolve(__dirname, '..');
const parentRoot = path.resolve(
  __dirname,
  '../../react-native-mapsforge-vtm'
);

/**
 * Metro configuration
 */
const config = getDefaultConfig(__dirname);

module.exports = {
  ...config,
  watchFolders: [...(config.watchFolders ?? []), root, parentRoot],
};
