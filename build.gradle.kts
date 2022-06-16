plugins {
    kotlin("jvm")
    // Støtter unicode filer (i motsetning til https://github.com/JLLeitschuh/ktlint-gradle 10.0.0) og har nyere dependencies som gradle. Virker som den oppdateres hyppigere.
    id("org.jmailen.kotlinter") version "3.10.0"
}

version = "0.0.1"

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jmailen.kotlinter")
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/releases/")
        maven("https://packages.confluent.io/maven/")
    }
    val junitJupiterVersion = "5.8.2"
    val kotestVersion = "5.3.1"
    val jacksonVersion = "2.13.3"
    val kotlinVersion: String by this
    dependencies {
        api(kotlin("stdlib-jdk8"))

        implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
        implementation("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
        implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
        implementation("io.arrow-kt:arrow-core:1.1.2")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
        implementation("ch.qos.logback:logback-classic:1.2.11")
        implementation("net.logstash.logback:logstash-logback-encoder:7.2")
        implementation("io.github.cdimascio:dotenv-kotlin:6.3.1")
        implementation("org.apache.kafka:kafka-clients:3.1.0")
        implementation("com.networknt:json-schema-validator:1.0.70")
        implementation("no.finn.unleash:unleash-client-java:4.4.1")

        implementation("com.ibm.mq:com.ibm.mq.allclient:9.2.5.0")
        implementation("io.confluent:kafka-avro-serializer:7.1.0")
        implementation("org.apache.avro:avro:1.11.0")
        implementation("com.github.ben-manes.caffeine:caffeine:3.1.1")
        implementation("io.micrometer:micrometer-core:1.9.1")
        implementation("io.micrometer:micrometer-registry-prometheus:1.9.0")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
        testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.2.5")
        testImplementation("io.kotest:kotest-extensions:$kotestVersion")
        testImplementation("org.skyscreamer:jsonassert:1.5.0")
        testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
        // Embedded database brukes av modulene: web og database
        testImplementation(
            // select version() i preprod -> PostgreSQL 11.7 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 4.8.5 20150623 (Red Hat 4.8.5-39), 64-bit
            // The releases without the -1 suffix has a dyld/dylib issue on MacOs (i.e. the 11.7.0 version won't work)
            enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:11.6.0-1"),
        )
        testImplementation("io.zonky.test:embedded-postgres:2.0.0")

        constraints {
            implementation("commons-collections:commons-collections") {
                because("org.apache.cxf:cxf-rt-ws-security@3.4.4 -> https://app.snyk.io/vuln/SNYK-JAVA-COMMONSCOLLECTIONS-30078 and https://snyk.io/vuln/SNYK-JAVA-COMMONSCOLLECTIONS-472711")
                version {
                    require("3.2.2")
                }
            }
            implementation("io.netty:netty-codec-http2") {
                because("io.netty:netty-codec@4.1.63.Final and io.ktor:ktor-server-netty@1.6.4 -> https://snyk.io/vuln/SNYK-JAVA-IONETTY-2314893")
                version {
                    require("4.1.71.Final")
                }
            }
            implementation("com.google.code.gson:gson") {
                because("no.finn.unleash:unleash-client-java@4.4.1 -> https://security.snyk.io/vuln/SNYK-JAVA-COMGOOGLECODEGSON-1730327")
                version {
                    require("2.8.9")
                }
            }
            implementation("org.postgresql:postgresql") {
                because("io.zonky.test:embedded-postgres@1.3.1 -> https://snyk.io/vuln/SNYK-JAVA-ORGPOSTGRESQL-2390459")
                version {
                    require("42.3.2")
                }
            }
            implementation("com.google.protobuf:protobuf-java") {
                because("no.nav:kafka-embedded-env@2.8.1 -> https://snyk.io/vuln/SNYK-JAVA-COMGOOGLEPROTOBUF-2331703")
                version {
                    require("3.19.2")
                }
            }
            implementation("org.glassfish:jakarta.el") {
                because("no.nav:kafka-embedded-env@2.8.1 -> https://snyk.io/vuln/SNYK-JAVA-ORGGLASSFISH-1297098")
                version {
                    require("3.0.4")
                }
            }
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
            freeCompilerArgs += "-Xenable-builder-inference"
            freeCompilerArgs += "-progressive"
            allWarningsAsErrors = false
        }
    }

    tasks.withType<Wrapper> {
        gradleVersion = "7.4.2"
    }

    // Run `./gradlew allDeps` to get a dependency graph
    task("allDeps", DependencyReportTask::class) {}
}

configure(listOf(project(":client"))) {
    // TODO jah: We can't parallelize client at this point because of static usage of wiremockServer
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            // We only want to log failed and skipped tests when running Gradle.
            events("skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        failFast = false
    }
}

configure(
    listOf(
        project(":common"),
        project(":domain"),
        project(":test-common"),
        project(":service"),
        project(":web"),
        project(":database"),
        project(":web-regresjonstest"),
    ),
) {
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            // We only want to log failed and skipped tests when running Gradle.
            events("skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        // https://docs.gradle.org/current/userguide/performance.html#suggestions_for_java_projects
        failFast = false
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
        // https://junit.org/junit5/docs/snapshot/user-guide/#writing-tests-parallel-execution
        systemProperties["junit.jupiter.execution.parallel.enabled"] = true
        systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
    }
}

tasks.check {
    // Må ligge på root nivå
    dependsOn("installKotlinterPrePushHook")
}

configurations {
    all {
        // Vi bruker logback og mener vi kan trygt sette en exclude på log4j: https://security.snyk.io/vuln/SNYK-JAVA-ORGAPACHELOGGINGLOG4J-2314720
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }
}
