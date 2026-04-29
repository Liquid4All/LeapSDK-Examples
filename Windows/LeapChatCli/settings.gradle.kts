pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
  }
}

dependencyResolutionManagement {
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
