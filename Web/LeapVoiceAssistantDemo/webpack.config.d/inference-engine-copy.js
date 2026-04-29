/**
 * Copy inference_engine.js / inference_engine.wasm / inference_engine_worker.js
 * (extracted from leap-sdk-wasm-js-<ver>.klib by the extractWasmVendor Gradle task)
 * into the webpack output bundle. Works for both `wasmJsBrowserDevelopmentRun`
 * (served from memory by webpack-dev-server) and `wasmJsBrowserDistribution`
 * (written to dist/wasmJs/productionExecutable/).
 *
 * __dirname here resolves to build/wasm/packages/<project>/ (the generated webpack
 * config dir), so three levels up reaches the Gradle build/ root, then wasmStatic/wasm.
 */
const CopyWebpackPlugin = require('copy-webpack-plugin');

config.plugins = config.plugins || [];
config.plugins.push(
  new CopyWebpackPlugin({
    patterns: [
      {
        from: require('path').resolve(__dirname, '../../../wasmStatic/wasm'),
        to: '[name][ext]',
        noErrorOnMissing: false,
      },
    ],
  })
);
