plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ktfmt)
}

kotlin { jvmToolchain(17) }

ktfmt { googleStyle() }

android {
  namespace = "com.tldw.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.tldw.app"
    minSdk = 31
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  buildFeatures { compose = true }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.material.icons.extended)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.okhttp)
  implementation(libs.gson)
  implementation(libs.leap.sdk.android)
  implementation(libs.leap.model.downloader)
  implementation(libs.coil.compose)
  implementation(libs.compose.markdown)

  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.okhttp.mockwebserver)
}
