const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const config = getDefaultConfig(__dirname);

// Clerk-expo relies on clerk-react which needs react-dom and mjs
config.resolver.sourceExts.push('mjs', 'cjs');

config.resolver.extraNodeModules = {
    ...config.resolver.extraNodeModules,
    'react-dom': path.resolve(__dirname, 'node_modules/react-dom'),
};

module.exports = config;



