plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.application)
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  implementation(libs.leap.sdk)
  implementation(libs.kotlinx.coroutines.core)
}

application {
  mainClass.set("ai.liquid.leap.cli.MainKt")
  applicationName = "leap-chat-cli"
}
