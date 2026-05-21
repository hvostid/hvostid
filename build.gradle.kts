plugins {
    java
    alias(libs.plugins.owasp.dependency.check)
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.spotless) apply false
    jacoco
}

sonarqube {
    properties {
        property("sonar.projectKey", "hvostid")
        property("sonar.projectName", "HvostID")
    }
}

tasks.named("sonar") {
    dependsOn(subprojects.map { "${it.path}:jacocoTestReport" })
}

dependencyCheck {
    failBuildOnCVSS = 9.0f
    suppressionFile = "dependency-check-suppressions.xml"
    data.directory = "${rootProject.layout.buildDirectory.get()}/dependency-check-data"
    nvd {
        apiKey = System.getenv("NVD_API_KEY")
        validForHours = 24
    }
    formats = listOf("HTML", "JSON", "SARIF", "XML")
}

allprojects {
    group = "ru.hvostid"
    version = "0.1.0-SNAPSHOT"

    repositories {
        maven("https://repo.spring.io/snapshot")
        maven("https://repo.spring.io/milestone")
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    apply(plugin = rootProject.libs.plugins.sonarqube.get().pluginId)
    apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(rootProject.libs.versions.java.get().toInt())
        }
    }

    plugins.withId(rootProject.libs.plugins.spring.dependency.management.get().pluginId) {
        extra["tomcat.version"] = rootProject.libs.versions.tomcat.get()
        extra["postgresql.version"] = rootProject.libs.versions.postgresql.get()
        extra["netty.version"] = rootProject.libs.versions.netty.get()
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on")) {
                useVersion(rootProject.libs.versions.bouncycastle.get())
            }
            // springdoc 3.0.3 pins swagger-ui 5.32.2 which bundles DOMPurify 3.3.2
            // (CVE-2026-41238/9/40 + GHSA-39q2-94rc-95cp). 5.32.5 ships DOMPurify
            // 3.4.0 with the fix; bump the webjar without changing springdoc.
            if (requested.group == "org.webjars" && requested.name == "swagger-ui") {
                useVersion(rootProject.libs.versions.swagger.ui.get())
            }
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        systemProperty(
            "testcontainers.postgres.image",
            rootProject.libs.versions.postgres.image.get()
        )
        systemProperty(
            "testcontainers.minio.image",
            rootProject.libs.versions.minio.image.get()
        )
        systemProperty(
            "testcontainers.redis.image",
            rootProject.libs.versions.redis.image.get()
        )
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.jacocoTestReport {
        // Only run after tests have produced an .exec file.
        dependsOn(tasks.withType<Test>())
        reports {
            xml.required = true
        }
    }

    sonar {
        properties {
            property(
                "sonar.coverage.jacoco.xmlReportPaths",
                tasks.named<JacocoReport>("jacocoTestReport").flatMap { it.reports.xml.outputLocation }
            )
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            palantirJavaFormat(rootProject.libs.versions.palantir.java.format.get())
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.named("check") {
        dependsOn("spotlessCheck")
    }
}
