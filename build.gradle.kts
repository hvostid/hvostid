plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.sonarqube)
    jacoco
}

sonarqube {
    properties {
        property("sonar.projectKey", "hvostid")
        property("sonar.projectName", "HvostID")
    }
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

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(rootProject.libs.versions.java.get().toInt())
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        systemProperty(
            "testcontainers.postgres.image",
            rootProject.libs.versions.postgres.image.get()
        )
    }

    tasks.jacocoTestReport {
        reports {
            xml.required = true
        }
    }
}
