/**
 * Webpack dev-server proxy for local development.
 *
 * Proxies /api requests to https://leap.liquid.ai so the browser does not hit the
 * Leap API directly (which would be blocked by CORS when running on localhost).
 *
 * LeapDownloaderPlatform.kt detects localhost and uses a relative base URL so that
 * Ktor constructs paths like /api/edge-sdk/... that are intercepted here. No explicit
 * baseUrl configuration is required in consumer code.
 */
config.devServer = config.devServer || {};
config.devServer.proxy = [
  {
    context: ["/api"],
    target: "https://leap.liquid.ai",
    changeOrigin: true,
    secure: true,
  },
];
