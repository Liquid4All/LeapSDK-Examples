import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  kotlin("multiplatform")
  alias(libs.plugins.jetbrains.compose)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.com.ncorti.ktfmt.gradle)
  alias(libs.plugins.detekt)
}

kotlin {
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser { commonWebpackConfig { outputFileName = "leap-ui-demo-web.js" } }
    binaries.executable()
  }

  sourceSets {
    val wasmJsMain by getting {
      dependencies {
        implementation(project(":leap-sdk"))
        implementation(project(":leap-ui"))
        implementation(libs.jetbrains.compose.runtime)
        implementation(libs.jetbrains.compose.ui)
        implementation(libs.jetbrains.compose.foundation)
        implementation(libs.jetbrains.compose.material3)
      }
    }
  }
}

ktfmt { googleStyle() }

detekt { config.setFrom(rootProject.file("config/detekt/detekt.yml")) }
