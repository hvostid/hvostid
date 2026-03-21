plugins {
    id("java-library")
}

dependencies {
    api(libs.jackson.annotations)
    api(libs.jakarta.validation)

    testImplementation(libs.junit.jupiter)
}
