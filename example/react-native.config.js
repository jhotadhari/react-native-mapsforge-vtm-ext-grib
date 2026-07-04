const path = require('path');
const pkg = require('../package.json');

module.exports = {
  project: {},
  dependencies: {
    [pkg.name]: {
      root: path.join(__dirname, '..'),
      platforms: {
        // Codegen script incorrectly fails without this
        // So we explicitly specify the platforms with empty object
        android: {},
      },
    },
    'react-native-mapsforge-vtm': {
      root: path.join(__dirname, '../../react-native-mapsforge-vtm'),
      platforms: {
        android: {},
      },
    },
  },
};
