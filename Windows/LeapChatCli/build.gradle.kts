plugins {
  alias(libs.plugins.kotlin.multiplatform)
  // Auto-wires leap-sdk-mingwx64:<ver>:natives@zip and extracts inference_engine.dll +
  // libinference_engine_llamacpp_backend.dll + ie_zip.dll alongside the produced .exe so
  // standard Windows DLL co-location loads them at runtime.
  alias(libs.plugins.leap.sdk.native.libs)
}

kotlin {
  mingwX64 {
    binaries {
      executable {
        entryPoint = "ai.liquid.leap.cli.main"
        baseName = "leap-chat-cli"
      }
    }
  }

  sourceSets {
    val mingwX64Main by getting {
      dependencies {
        implementation(libs.leap.sdk)
        implementation(libs.kotlinx.coroutines.core)
        // Ktor Curl engine for TLS on Kotlin/Native (the leap-sdk's bundled CIO
        // engine doesn't support TLS on Native; injecting HttpClient(Curl) into
        // LeapDownloader fixes HTTPS calls to leap.liquid.ai). Windows variant
        // statically links libcurl + openssl, so no system-side install needed.
        implementation(libs.ktor.client.curl)
      }
    }
  }
}
