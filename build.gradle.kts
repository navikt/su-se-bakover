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
    val kotestVersion = "5.8.1"
    val jacksonVersion = "2.17.0"
    val confluentVersion = "7.6.0"
    dependencies {
        implementation(rootProject.libs.kotlin.reflect)
        implementation(rootProject.libs.kotlin.script.runtime)
        implementation(rootProject.libs.kotlin.compiler.embeddable)
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
        implementation(platform("io.arrow-kt:arrow-stack:1.2.3"))
        implementation("io.arrow-kt:arrow-core")
        implementation("io.arrow-kt:arrow-fx-coroutines")
        implementation("io.arrow-kt:arrow-fx-stm")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
        implementation(rootProject.libs.slf4j.api)
        implementation(rootProject.libs.jul.to.slf4j)
        implementation(rootProject.libs.jcl.over.slf4j)
        implementation(rootProject.libs.log4j.over.slf4j)
        implementation("ch.qos.logback:logback-classic:1.5.3")
        implementation("net.logstash.logback:logstash-logback-encoder:7.4")
        implementation("com.papertrailapp", "logback-syslog4j", "1.0.0")
        implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
        implementation("com.networknt:json-schema-validator:1.4.0")
        implementation("com.ibm.mq:com.ibm.mq.allclient:9.3.5.0")
        implementation("org.apache.kafka:kafka-clients:3.7.0") {
            exclude("org.apache.kafka", "kafka-raft")
            exclude("org.apache.kafka", "kafka-server-common")
            exclude("org.apache.kafka", "kafka-storage")
            exclude("org.apache.kafka", "kafka-storage-api")
            exclude("org.apache.kafka", "kafka-streams")
        }
        implementation("io.confluent:kafka-avro-serializer:$confluentVersion") {
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
        implementation("org.apache.avro:avro:1.11.3") {
            exclude("org.apache.commons", "commons-compress")
        }
        implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
        implementation("io.micrometer:micrometer-core:1.12.4")
        implementation("io.micrometer:micrometer-registry-prometheus:1.12.4")
        implementation("com.github.seratch:kotliquery:1.9.0")
        implementation("org.flywaydb:flyway-core:10.10.0")
        implementation("org.flywaydb:flyway-database-postgresql:10.10.0")
        implementation("com.zaxxer:HikariCP:5.1.0")
        implementation("com.github.navikt:vault-jdbc:1.3.10")
        implementation("org.postgresql:postgresql:42.7.3") {
            exclude("org.apache.commons", "commons-compress")
        }
        // Brukes av avro?
        implementation("org.apache.commons:commons-compress:1.26.1")


        implementation(rootProject.libs.ktor.server.netty)
        implementation(rootProject.libs.ktor.server.auth.jwt) {
            exclude(group = "junit")
        }
        implementation(rootProject.libs.ktor.server.metrics.micrometer)
        implementation(rootProject.libs.ktor.serialization.jackson)
        implementation(rootProject.libs.ktor.server.content.negotiation)
        implementation(rootProject.libs.ktor.server.call.id)
        implementation(rootProject.libs.ktor.server.call.logging)
        implementation(rootProject.libs.ktor.server.forwarded.header)
        implementation(rootProject.libs.ktor.server.status.pages)

        // We exclude jdk15on because of security issues. We use jdk18on instead.
        implementation("org.bouncycastle:bcprov-jdk18on:1.77")

        testRuntimeOnly(rootProject.libs.jupiter.engine)

        testImplementation(rootProject.libs.jupiter.api)
        testImplementation(rootProject.libs.jupiter.params)
        testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
        testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
        testImplementation("io.kotest:kotest-extensions:$kotestVersion")
        testImplementation("org.skyscreamer:jsonassert:1.5.1")
        testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
        // Embedded database brukes av modulene: web og database
        testImplementation(
            // select version() i preprod 2023-11-02 -> PostgreSQL 11.16 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 4.8.5 20150623 (Red Hat 4.8.5-44), 64-bit
            // select version() i prod 2023-11-02 -> PostgreSQL 11.16 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 4.8.5 20150623 (Red Hat 4.8.5-44), 64-bit
            // Merk at det ikke har blitt kompilert så mange drivere for denne: https://mvnrepository.com/artifact/io.zonky.test.postgres/embedded-postgres-binaries-darwin-arm64v8 og kun en versjon for postgres 11
            enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:15.5.1"),
        )
        testImplementation("io.zonky.test:embedded-postgres:2.0.6") {
            exclude("org.apache.commons", "commons-compress")
        }
        // Legger til manglende binaries for nye Mac's med M1 cpuer.
        testImplementation("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8") {
            exclude("org.apache.commons", "commons-compress")
        }
        testImplementation("org.xmlunit:xmlunit-matchers:2.9.1")
        testImplementation(rootProject.libs.ktor.server.test.host) {
            exclude(group = "junit")
            exclude(group = "org.eclipse.jetty") // conflicts with WireMock
            exclude(group = "org.eclipse.jetty.http2") // conflicts with WireMock
        }

        constraints {
            implementation("org.xerial.snappy:snappy-java") {
                because("https://github.com/navikt/su-se-bakover/security/dependabot/12 https://github.com/advisories/GHSA-55g7-9cwv-5qfv")
                version {
                    require("1.1.10.4")
                }
            }
            implementation("org.eclipse.jgit:org.eclipse.jgit") {
                because("Affected <= 6.6.0.202305301015-r https://github.com/navikt/su-se-bakover/security/dependabot/11 https://github.com/advisories/GHSA-3p86-9955-h393")
                version {
                    require("6.7.0.202309050840-r")
                }
            }
            implementation("io.netty:netty-handler") {
                because("https://github.com/navikt/su-se-bakover/security/dependabot/3 https://github.com/advisories/GHSA-6mjq-h674-j845")
                version {
                    require("4.1.98.Final")
                }
            }
            implementation("com.google.guava:guava") {
                because("https://github.com/navikt/su-se-bakover/security/dependabot/2 https://github.com/advisories/GHSA-7g45-4rm6-3mm3 https://github.com/navikt/su-se-bakover/security/dependabot/7 https://github.com/advisories/GHSA-5mg8-w23w-74h3")
                version {
                    require("32.1.2-jre")
                }
            }
            implementation("com.google.j2objc:j2objc-annotations") {
                because("Required by: com.google.guava:guava:32.1.2-jre")
                version {
                    require("2.8")
                }
            }
            implementation("org.apache.santuario:xmlsec") {
                because("https://github.com/navikt/su-se-bakover/security/dependabot/17 https://github.com/advisories/GHSA-xfrj-6vvc-3xm2")
                version {
                    require("2.3.4")
                }
            }
            implementation("com.squareup.okio:okio") {
                because("https://github.com/navikt/su-se-bakover/security/dependabot/19 https://github.com/advisories/GHSA-w33c-445m-f8w7")
                version {
                    require("3.4.0")
                }
            }
            implementation("com.squareup.okio:okio-jvm") {
                because("https://github.com/navikt/su-se-bakover/security/dependabot/20 https://github.com/advisories/GHSA-w33c-445m-f8w7")
                version {
                    require("3.4.0")
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
