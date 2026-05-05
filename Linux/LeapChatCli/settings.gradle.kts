pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    maven {
      name = "Central Portal Snapshots"
      url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
    mavenLocal()
  }
}

dependencyResolutionManagement {
  // PREFER_SETTINGS so the Kotlin/Native plugin can register its own toolchain
  // ivy repos for konan / yarn distributions; ours below win for the leap SDK
  // and the native-libs-plugin.
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    mavenLocal()
    mavenCentral()
    maven {
      name = "Central Portal Snapshots"
      url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
  }
}

rootProject.name = "LeapChatCli"
