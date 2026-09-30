rootProject.name = "hvostid"

pluginManagement {
    repositories {
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
