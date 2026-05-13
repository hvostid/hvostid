plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.dependencies.get().toString())
    }
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.doc.openapi.webmvc)
    implementation(libs.jackson.databind)
    implementation(project(":common"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.awaitility)
    testImplementation(testFixtures(project(":common")))
    testRuntimeOnly(libs.junit.platform.launcher)
}
