import java.security.MessageDigest

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
    failOnError = true
    suppressionFile = "dependency-check-suppressions.xml"
    data.directory = "${rootProject.layout.buildDirectory.get()}/dependency-check-data"
    outputDirectory.set(layout.buildDirectory.dir("reports"))
    nvd {
        apiKey = System.getenv("NVD_API_KEY")
        // CI bootstraps an empty database from full official feeds, then clears
        // the feed URL and forces an API catch-up before saving a valid cache.
        providers.gradleProperty("nvdDatafeedUrl").orNull?.let { datafeedUrl = it }
        validForHours = providers.gradleProperty("nvdValidForHours").map {
            it.toInt().also { hours -> require(hours == 0 || hours == 24) }
        }.getOrElse(24)
    }
    formats = listOf("HTML", "JSON", "SARIF", "XML")
}

// DC13's task DataExtension sets a Gradle-home default that overrides the
// extension convention. Set the actual task property so update, scan and purge
// all use the database restored and saved by CI.
tasks.withType<org.owasp.dependencycheck.gradle.tasks.ConfiguredTask>().configureEach {
    data.directory.set(layout.buildDirectory.dir("dependency-check-data").map { it.asFile.path })
}

val exportSecurityRuntimeInventory = tasks.register("exportSecurityRuntimeInventory") {
    description = "Export resolved external runtime artifacts for dependency scan completeness checks."
    val destination = layout.buildDirectory.file("reports/jvm-runtime-inventory.json")
    outputs.file(destination)
    // Regenerate from the same resolution used by the scan, never a cached inventory.
    outputs.upToDateWhen { false }
    doLast {
        val services = listOf("api-gateway", "auth-service", "listing-service", "passport-service", "matching-service")
        val artifacts = services.flatMap { service ->
            val runtime = project(":$service").configurations.getByName("runtimeClasspath")
            val external = runtime.incoming.artifactView {
                componentFilter { it is org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            }.artifacts.artifacts
            require(external.isNotEmpty()) { "No external runtime artifacts for $service" }
            external.map { artifact ->
                val module = artifact.id.componentIdentifier as org.gradle.api.artifacts.component.ModuleComponentIdentifier
                val digest = MessageDigest.getInstance("SHA-256")
                artifact.file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var length = input.read(buffer)
                    while (length != -1) {
                        digest.update(buffer, 0, length)
                        length = input.read(buffer)
                    }
                }
                mapOf(
                    "service" to service,
                    "group" to module.group,
                    "name" to module.module,
                    "version" to module.version,
                    "fileName" to artifact.file.name,
                    "sha256" to digest.digest().joinToString("") { "%02x".format(it) }
                )
            }.sortedWith(compareBy({ it["group"] }, { it["name"] }, { it["version"] }, { it["fileName"] }))
        }
        val report = destination.get().asFile
        report.parentFile.mkdirs()
        report.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(
            mapOf("schema" to 1, "services" to services, "artifacts" to artifacts)
        )) + "\n")
        logger.lifecycle("Exported ${artifacts.size} external runtime artifacts across ${services.size} services to $report")
    }
}

tasks.named("dependencyCheckAggregate") {
    dependsOn(exportSecurityRuntimeInventory)
}

allprojects {
    group = "ru.hvostid"
    version = "0.1.0-SNAPSHOT"

    repositories {
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
        extra["jackson-bom.version"] = "3.2.3"
        extra["jackson-2-bom.version"] = "2.22.3"
        extra["log4j2.version"] = "2.26.1"
        extra["kotlin.version"] = "2.4.20"
        extra["opentelemetry.version"] = "1.62.0"
    }

    plugins.withId(rootProject.libs.plugins.spring.boot.get().pluginId) {
        dependencies.add("runtimeOnly", "io.micrometer:micrometer-registry-prometheus")
        dependencies.add("runtimeOnly", "org.springframework.boot:spring-boot-starter-opentelemetry")
        dependencies.add("runtimeOnly", "org.springframework.boot:spring-boot-starter-restclient")
    }

    dependencyLocking {
        lockAllConfigurations()
    }

    tasks.register("resolveAndLockDependencies") {
        description = "Resolve every dependency configuration and update checked-in lockfiles."
        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks) { "Run with --write-locks" }
        }
        doLast {
            configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
        }
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on")) {
                useVersion(rootProject.libs.versions.bouncycastle.get())
            }
            // Keep the embedded UI aligned with the independently patched webjar.
            if (requested.group == "org.webjars" && requested.name == "swagger-ui") {
                useVersion(rootProject.libs.versions.swagger.ui.get())
            }
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        systemProperty("management.otlp.metrics.export.enabled", "false")
        systemProperty("management.opentelemetry.tracing.export.otlp.enabled", "false")
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

    tasks.jacocoTestCoverageVerification {
        dependsOn(tasks.withType<Test>())
        violationRules {
            rule {
                element = "CLASS"
                includes = listOf(
                    "ru.hvostid.gateway.filter.TokenIntrospectionFilter",
                    "ru.hvostid.auth.service.SessionCleanupService",
                    "ru.hvostid.passport.service.PassportDocumentValidator",
                    "ru.hvostid.matching.service.MatchRecommendationsService"
                )
                limit {
                    counter = "LINE"
                    minimum = "0.70".toBigDecimal()
                }
            }
        }
    }

    sonar {
        properties {
            // The Sonar Gradle plugin (7.3.0) does not unwrap Provider values
            // and ends up writing the toString() form. Resolve to a plain
            // path string up front so the scanner reads an actual file.
            property(
                "sonar.coverage.jacoco.xmlReportPaths",
                layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.path
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
        dependsOn("jacocoTestCoverageVerification")
    }
}
