plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "hvostid"

include(
    "common",
    "api-gateway",
    "auth-service",
    "listing-service",
    "passport-service",
    "matching-service"
)
