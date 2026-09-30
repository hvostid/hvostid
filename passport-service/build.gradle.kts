plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

val minioTestContext = rootProject.layout.projectDirectory.dir("docker/minio")
val prepareMinioTestImage by tasks.registering(Exec::class) {
    group = "verification"
    description = "Build the patched production MinIO source as the test image using Docker's layer cache."
    commandLine(
        "docker", "build", "--tag", rootProject.libs.versions.minio.image.get(),
        minioTestContext.asFile.absolutePath
    )
}

tasks.test {
    dependsOn(prepareMinioTestImage)
    inputs.dir(minioTestContext)
}

dependencies {
    implementation(libs.apache.pdfbox)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.doc.openapi.webmvc)
    implementation(libs.minio)
    implementation(project(":common"))

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.flyway.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.minio)
    testImplementation(testFixtures(project(":common")))
    testRuntimeOnly(libs.junit.platform.launcher)
}
