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
    compileOnly(libs.swagger.annotations)

    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(libs.spring.boot.testcontainers)
    testFixturesApi(libs.testcontainers.junit)
    testFixturesApi(libs.testcontainers.postgresql)
    // OpenApiDocsExporter uses MockMvc#perform(...).getResponse() which
    // returns a jakarta.servlet.http.HttpServletResponse, so the servlet
    // API must be on the testFixtures compile classpath.
    testFixturesCompileOnly(libs.jakarta.servlet.api)
    testFixturesRuntimeOnly(libs.postgresql)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
