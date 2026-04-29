import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.com.ncorti.ktfmt.gradle)
}

android {
  namespace = "ai.liquid.leap.uidemo"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "ai.liquid.leap.uidemo"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
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
  lint { targetSdk = libs.versions.targetSdk.get().toInt() }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.material3)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.leap.sdk)
  implementation(libs.leap.model.downloader)
  implementation(libs.leap.ui)
  debugImplementation(libs.androidx.ui.tooling)
}

ktfmt { googleStyle() }
