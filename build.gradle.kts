plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.jacoco)
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
        maven { url = uri("https://repo.spring.io/snapshot") }
        maven { url = uri("https://repo.spring.io/milestone") }
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    apply(plugin = "org.sonarqube")

    java {
        toolchain {
            languageVersion.set(
                JavaLanguageVersion.of(rootProject.libs.versions.java.get().toInt())
            )
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.jacocoTestReport {
        reports {
            xml.required.set(true)
        }
    }
}
