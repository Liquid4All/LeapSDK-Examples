import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.com.ncorti.ktfmt.gradle)
  alias(libs.plugins.detekt)
}

android {
  namespace = "ai.liquid.leap.uidemo"
  compileSdk = libs.versions.compileSdk.toInt()

  defaultConfig {
    applicationId = "ai.liquid.leap.uidemo"
    minSdk = libs.versions.minSdk.toInt()
    targetSdk = libs.versions.targetSdk.toInt()
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }
  buildFeatures { compose = true }
  lint { targetSdk = libs.versions.targetSdk.toInt() }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.material3)
  implementation(libs.kotlinx.coroutines.android)
  implementation(project(":leap-sdk"))
  implementation(project(":leap-ui"))
  debugImplementation(libs.androidx.ui.tooling)
}

ktfmt { googleStyle() }

detekt {
  config.setFrom(rootProject.file("config/detekt/detekt.yml"))
  baseline = file("detekt-baseline.xml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
  if (name == "detekt") {
    source("src/main/kotlin")
  }
}
