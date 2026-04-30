pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
    maven {
      name = "Central Portal Snapshots"
      url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
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
