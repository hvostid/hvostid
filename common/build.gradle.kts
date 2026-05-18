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
    compileOnly(libs.spring.security.config)
    compileOnly(libs.spring.security.web)
    compileOnlyApi(libs.jakarta.servlet.api)
    compileOnly(libs.swagger.annotations)
    compileOnly(libs.swagger.models)

    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.slf4j:slf4j-api")

    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(libs.spring.boot.testcontainers)
    testFixturesApi(libs.testcontainers.junit)
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesRuntimeOnly(libs.postgresql)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework:spring-webmvc")
    testImplementation("org.springframework:spring-test")
    testImplementation(libs.spring.security.core)
    testImplementation(libs.jakarta.servlet.api)
    testImplementation("org.hibernate.validator:hibernate-validator")
    testRuntimeOnly("org.apache.tomcat.embed:tomcat-embed-el")
    testRuntimeOnly(libs.junit.platform.launcher)
}
