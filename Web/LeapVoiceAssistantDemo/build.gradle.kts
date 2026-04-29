import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.jetbrains.compose)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.com.ncorti.ktfmt.gradle)
}

kotlin {
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      commonWebpackConfig { outputFileName = "leap-voice-assistant-demo-web.js" }
    }
    binaries.executable()
  }

  sourceSets {
    val wasmJsMain by getting {
      dependencies {
        implementation(libs.leap.sdk)
        implementation(libs.leap.ui)
        implementation(libs.jetbrains.compose.runtime)
        implementation(libs.jetbrains.compose.ui)
        implementation(libs.jetbrains.compose.foundation)
        implementation(libs.jetbrains.compose.material3)
        implementation(npm("copy-webpack-plugin", "12.0.2"))
      }
    }
  }
}

ktfmt { googleStyle() }

// Extract inference_engine.{js,wasm,_worker.js} from the leap-sdk-wasm-js klib so
// CopyWebpackPlugin (configured in webpack.config.d/inference-engine-copy.js) can
// reference them at a stable path. The klib is just a zip; its `wasm/` dir holds the
// native binaries that webpack's bundle output needs alongside the Kotlin/Wasm code.
val extractWasmVendor by
  tasks.registering(Copy::class) {
    from(
      provider {
        configurations
          .named("wasmJsRuntimeClasspath")
          .get()
          .files
          .filter { it.name.startsWith("leap-sdk-wasm-js") && it.extension == "klib" }
          .map { zipTree(it) }
      }
    ) {
      include("wasm/**")
    }
    into(layout.buildDirectory.dir("wasmStatic"))
  }

// Wire extractWasmVendor into every webpack-touching task so dev (`…DevelopmentRun`)
// and prod (`…Distribution`) flows both produce the wasm files before CopyWebpackPlugin runs.
listOf(
    "wasmJsBrowserDevelopmentRun",
    "wasmJsBrowserDevelopmentWebpack",
    "wasmJsBrowserDevelopmentExecutableDistribution",
    "wasmJsBrowserProductionWebpack",
    "wasmJsBrowserDistribution",
  )
  .forEach { taskName -> tasks.matching { it.name == taskName }.configureEach { dependsOn(extractWasmVendor) } }
