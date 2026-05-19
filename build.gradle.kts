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

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.apache.tomcat.embed") {
                useVersion(rootProject.libs.versions.tomcat.get())
                because("CVE-2026-41293, 43512, 43515, 41284, 42498, 43513, 43514")
            }

            if (requested.group == "org.postgresql" && requested.name == "postgresql") {
                useVersion(rootProject.libs.versions.postgresql.get())
                because("CVE-2026-42198")
            }

            if (requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on")) {
                useVersion(rootProject.libs.versions.bouncycastle.get())
                because("CVE-2026-5598, CVE-2026-0636")
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
    }

    tasks.jacocoTestReport {
        reports {
            xml.required = true
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
