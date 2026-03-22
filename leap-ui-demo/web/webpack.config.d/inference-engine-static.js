/**
 * Serve inference_engine.js / inference_engine.wasm / inference_engine_worker.js
 * at the webpack dev-server root so they are accessible as ./inference_engine.js etc.
 *
 * The files are installed by `./gradlew installVendor` into
 * leap-sdk/src/wasmJsMain/resources/wasm/ and are not bundled by webpack.
 *
 * __dirname here resolves to build/wasm/packages/LeapSDK-leap-ui-demo-web/ (the
 * generated webpack config directory), so four levels up reaches the worktree root.
 */
config.devServer = config.devServer || {};
config.devServer.static = config.devServer.static || [];
config.devServer.static.push({
  directory: require('path').resolve(__dirname, '../../../../leap-sdk/src/wasmJsMain/resources/wasm'),
  publicPath: '/',
});
