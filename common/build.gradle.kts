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

val compileOnlyAndTest by configurations.creating
configurations.compileOnly.get().extendsFrom(compileOnlyAndTest)
configurations.testImplementation.get().extendsFrom(compileOnlyAndTest)
configurations.named("testFixturesCompileOnly").configure { extendsFrom(compileOnlyAndTest) }

dependencies {
    api(libs.jackson.annotations)
    api(libs.jakarta.validation)
    api(libs.logstash.logback.encoder)

    compileOnlyAndTest(libs.spring.security.core)
    compileOnlyAndTest(libs.spring.security.config)
    compileOnlyAndTest(libs.spring.security.web)
    compileOnlyAndTest(libs.jakarta.servlet.api)
    compileOnlyAndTest(libs.spring.web)
    compileOnlyAndTest(libs.spring.webmvc)
    compileOnlyAndTest(libs.spring.context)
    compileOnlyAndTest(libs.spring.boot.autoconfigure)
    compileOnlyAndTest(libs.slf4j.api)
    compileOnly(libs.swagger.annotations)
    compileOnly(libs.swagger.models)

    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(libs.spring.boot.testcontainers)
    testFixturesApi(libs.testcontainers.junit)
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesRuntimeOnly(libs.postgresql)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spring.test)
    testImplementation(libs.hibernate.validator)
    testRuntimeOnly(libs.tomcat.embed.el)
    testRuntimeOnly(libs.junit.platform.launcher)
}
