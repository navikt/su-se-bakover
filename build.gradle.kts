import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("com.diffplug.spotless") version "6.25.0"
}

version = "0.0.1"

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.diffplug.spotless")
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/releases/")
        maven("https://packages.confluent.io/maven/")
    }
    dependencies {
        implementation(rootProject.libs.kotlin.reflect)
        implementation(rootProject.libs.kotlin.script.runtime)
        implementation(rootProject.libs.kotlin.compiler.embeddable)
        implementation(rootProject.libs.kotlinx.coroutines.core)
        implementation(platform(rootProject.libs.arrow.stack))
        implementation(rootProject.libs.arrow.core)
        implementation(rootProject.libs.arrow.fx.coroutines)
        implementation(rootProject.libs.arrow.fx.stm)
        implementation(rootProject.libs.jackson.datatype.jsr310)
        implementation(rootProject.libs.jackson.datatype.jdk8)
        implementation(rootProject.libs.jackson.module.kotlin)
        implementation(rootProject.libs.jackson.dataformat.xml)
        implementation(rootProject.libs.slf4j.api)
        implementation(rootProject.libs.jul.to.slf4j)
        implementation(rootProject.libs.jcl.over.slf4j)
        implementation(rootProject.libs.log4j.over.slf4j)
        implementation(rootProject.libs.logback.classic)
        implementation(rootProject.libs.logstash.logback.encoder)
        implementation(rootProject.libs.logback.syslog4j)
        implementation(rootProject.libs.dotenv.kotlin)
        implementation(rootProject.libs.json.schema.validator)
        implementation(rootProject.libs.com.ibm.mq.allclient)
        implementation(rootProject.libs.kafka.clients) {
            exclude("org.apache.kafka", "kafka-raft")
            exclude("org.apache.kafka", "kafka-server-common")
            exclude("org.apache.kafka", "kafka-storage")
            exclude("org.apache.kafka", "kafka-storage-api")
            exclude("org.apache.kafka", "kafka-streams")
        }
        implementation(rootProject.libs.kafka.avro.serializer) {
            exclude("org.apache.kafka", "kafka-clients")
            exclude("io.confluent", "common-utils")
            exclude("io.confluent", "logredactor")
            exclude("org.apache.avro", "avro")
            exclude("org.apache.commons", "commons-compress")
            exclude("com.google.errorprone")
            exclude("org.checkerframework")
            exclude("com.google.j2objc")
            exclude("com.google.code.findbugs")
            exclude("io.swagger.core.v3")
        }
        implementation(rootProject.libs.avro) {
            exclude("org.apache.commons", "commons-compress")
        }
        implementation(rootProject.libs.caffeine)
        implementation(rootProject.libs.micrometer.core)
        implementation(rootProject.libs.micrometer.registry.prometheus)
        implementation(rootProject.libs.kotliquery)
        implementation(rootProject.libs.flyway.core)
        implementation(rootProject.libs.flyway.database.postgresql)
        implementation(rootProject.libs.hikaricp)
        implementation(rootProject.libs.vault.jdbc)
        implementation(rootProject.libs.postgresql) {
            exclude("org.apache.commons", "commons-compress")
        }
        // Brukes av avro?
        implementation(rootProject.libs.commons.compress)


        implementation(rootProject.libs.ktor.server.netty)
        implementation(rootProject.libs.ktor.server.auth.jwt) {
            exclude("junit")
        }
        implementation(rootProject.libs.ktor.server.metrics.micrometer)
        implementation(rootProject.libs.ktor.serialization.jackson)
        implementation(rootProject.libs.ktor.server.content.negotiation)
        implementation(rootProject.libs.ktor.server.call.id)
        implementation(rootProject.libs.ktor.server.call.logging)
        implementation(rootProject.libs.ktor.server.forwarded.header)
        implementation(rootProject.libs.ktor.server.status.pages)

        // We exclude jdk15on because of security issues. We use jdk18on instead.
        implementation(rootProject.libs.bcprov.jdk18on)

        testRuntimeOnly(rootProject.libs.jupiter.engine)

        testImplementation(rootProject.libs.jupiter.api)
        testImplementation(rootProject.libs.jupiter.params)
        testImplementation(rootProject.libs.kotest.assertions.core)
        testImplementation(rootProject.libs.kotest.assertions.json)
        testImplementation(rootProject.libs.kotest.extensions)
        testImplementation(rootProject.libs.kotest.assertions.arrow)


        testImplementation(rootProject.libs.jsonassert)
        testImplementation(rootProject.libs.mockito.kotlin)
        testImplementation(
            enforcedPlatform(rootProject.libs.embedded.postgres.binaries.bom),
        )
        testImplementation(rootProject.libs.embedded.postgres) {
            exclude("org.apache.commons", "commons-compress")
        }
        // Legger til manglende binaries for nye Mac's med M1 cpuer. (denne arver versjonen til embedded-postgres-binaries-bom)
        testImplementation(rootProject.libs.embedded.postgres.binaries.darwin.arm64v8) {
            exclude("org.apache.commons", "commons-compress")
        }
        testImplementation(rootProject.libs.xmlunit.matchers)
        testImplementation(rootProject.libs.ktor.server.test.host) {
            exclude(group = "junit")
            exclude(group = "org.eclipse.jetty") // conflicts with WireMock
            exclude(group = "org.eclipse.jetty.http2") // conflicts with WireMock
        }

        constraints {
            implementation("com.google.guava:guava") {
                because("https://github.com/navikt/su-se-bakover/security/dependabot/2 https://github.com/advisories/GHSA-7g45-4rm6-3mm3 https://github.com/navikt/su-se-bakover/security/dependabot/7 https://github.com/advisories/GHSA-5mg8-w23w-74h3")
                version {
                    require("32.1.2-jre")
                }
            }
        }
    }

    tasks.withType<KotlinJvmCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-progressive")
            allWarningsAsErrors.set(true)
        }
    }

    java {
        // Ensuring any java-files is also compiled with the preferred version.
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<Wrapper> {
        gradleVersion = "8.5"
    }

    // Run `./gradlew allDeps` to get a dependency graph
    task("allDeps", DependencyReportTask::class) {}

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            /** spotless støtter ikke .editorconfig enda så vi må duplisere den her :( */
            ktlint().editorConfigOverride(
                mapOf(
                    "indent_size" to 4,
                    "insert_final_newline" to true,
                    "ij_kotlin_allow_trailing_comma_on_call_site" to true,
                    "ij_kotlin_allow_trailing_comma" to true,
                ),
            )
            // jah: diktat er veldig intrusive - virker ikke som den gir så stor verdi uten å disable veldig mange regler.
            // jah: ktfmt er et alternativ til ktlint som vi kan vurdere bytte til på sikt. Skal være strengere som vil gjøre kodebasen mer enhetlig.
            // jah: prettier for kotlin virker umodent.
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
        }
    }
}

subprojects {
    tasks.test.configure {
        sharedTestSetup()
        maxParallelForks = (Runtime.getRuntime().availableProcessors() * 0.4).toInt().takeIf { it > 0 } ?: 1
        // https://junit.org/junit5/docs/snapshot/user-guide/#writing-tests-parallel-execution
        systemProperties["junit.jupiter.execution.parallel.enabled"] = true
        systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
        systemProperties["junit.jupiter.execution.parallel.mode.classes.default"] = "concurrent"
    }
}

configurations {
    all {
        // Vi bruker logback og mener vi kan trygt sette en exclude på log4j: https://security.snyk.io/vuln/SNYK-JAVA-ORGAPACHELOGGINGLOG4J-2314720
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
        // Ref dependabot PR 8 - https://github.com/navikt/su-se-bakover/security/dependabot/1 https://github.com/advisories/GHSA-6xx3-rg99-gc3p https://github.com/navikt/su-se-bakover/security/dependabot/8 https://github.com/advisories/GHSA-hr8g-6v94-x4m9
        // We exclude this and include jdk18on instead.
        exclude(group= "org.bouncycastle", module= "bcprov-jdk15on")
    }
}

fun Test.sharedTestSetup() {
    useJUnitPlatform()
    testLogging {
        // We only want to log failed and skipped tests when running Gradle.
        events("skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
    // https://docs.gradle.org/current/userguide/performance.html#suggestions_for_java_projects
    failFast = false
    // Enable withEnvironment https://kotest.io/docs/extensions/system_extensions.html
    jvmArgs = listOf("--add-opens", "java.base/java.util=ALL-UNNAMED")
}

tasks.register<Copy>("gitHooks") {
    from("scripts/hooks/pre-commit")
    into(".git/hooks")
}

tasks.named("build") {
    dependsOn(":gitHooks")
}
// TODO jah: Fix find + grep
//apply(from = "gradle/checkImports.gradle.kts")


tasks.register("verifyUniqueJarNames") {
    doLast {
        val allJarNames = allprojects.mapNotNull { project ->
            // :datapakker:soknad kjøres som egen pod og må hete app.jar (samme som application-modulen) pga. baseimages: https://github.com/navikt/baseimages/tree/master/java
            if (project.path == ":datapakker:soknad") return@mapNotNull null
            if (project.path == ":datapakker:fritekstAvslag") return@mapNotNull null
            project.tasks.findByName("jar")?.let {
                (it as? org.gradle.jvm.tasks.Jar)?.archiveBaseName?.get()
            }
        }
        val uniqueJarNames = allJarNames.toSet()
        if (allJarNames.size != uniqueJarNames.size) {
            val duplicateNames = allJarNames.groupingBy { it }
                .eachCount()
                .filter { it.value > 1 }
                .keys
            throw GradleException("Duplicate JAR names found: $duplicateNames. Please ensure all JAR names are unique.")
        }
        println("All JAR names are unique.")
    }
}
tasks.named("check") {
    dependsOn("verifyUniqueJarNames")
}
