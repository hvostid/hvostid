plugins {
    `java-library`
    `java-test-fixtures`
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.boot.dependencies.get().toString())
    }
}

dependencies {
    api(libs.jackson.annotations)
    api(libs.jakarta.validation)

    compileOnly(libs.spring.security.core)
    compileOnly(libs.spring.security.web)
    compileOnly(libs.jakarta.servlet.api)

    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(libs.spring.boot.testcontainers)
    testFixturesApi(libs.testcontainers.junit)
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesRuntimeOnly(libs.postgresql)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
