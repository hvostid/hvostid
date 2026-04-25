plugins {
    `java-library`
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

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
