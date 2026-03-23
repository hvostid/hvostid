rootProject.name = "hvostid"

pluginManagement {
    repositories {
        maven("https://repo.spring.io/snapshot")
        maven("https://repo.spring.io/milestone")
        gradlePluginPortal()
        mavenCentral()
    }
}

include(
    "common",
    "api-gateway",
    "auth-service",
    "listing-service",
    "passport-service",
    "matching-service"
)
