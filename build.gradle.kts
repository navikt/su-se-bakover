buildscript {
    dependencies {
        classpath("org.ajoberstar:grgit:2.3.0")
    }
}

plugins {
    kotlin("jvm")
    // Støtter unicode filer (i motsetning til https://github.com/JLLeitschuh/ktlint-gradle 10.0.0) og har nyere dependencies som gradle. Virker som den oppdateres hyppigere.
    id("org.jmailen.kotlinter") version "3.6.0"
}

version = "0.0.1"

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jmailen.kotlinter")
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/releases/")
        maven("https://packages.confluent.io/maven/")
    }
    val junitJupiterVersion = "5.8.1"
    val kotestVersion = "4.6.3"
    val jacksonVersion = "2.13.0"
    val kotlinVersion: String by this
    dependencies {
        api(kotlin("stdlib-jdk8"))

        implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
        implementation("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
        implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
        implementation("io.arrow-kt:arrow-core:1.0.1")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
        implementation("ch.qos.logback:logback-classic:1.2.6")
        implementation("net.logstash.logback:logstash-logback-encoder:6.6")
        implementation("io.github.cdimascio:dotenv-kotlin:6.2.2")
        implementation("org.apache.kafka:kafka-clients:3.0.0")
        implementation("com.networknt:json-schema-validator:1.0.63")
        implementation("no.finn.unleash:unleash-client-java:4.4.1")

        implementation("com.ibm.mq:com.ibm.mq.allclient:9.2.3.0")
        implementation("io.confluent:kafka-avro-serializer:6.2.1")
        implementation("org.apache.avro:avro:1.11.0")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
        testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.1.1")
        testImplementation("io.kotest:kotest-extensions:$kotestVersion")
        testImplementation("org.skyscreamer:jsonassert:1.5.0")
        testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
        // Embedded database brukes av modulene: web og database
        testImplementation(
            //select version() i preprod -> PostgreSQL 11.7 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 4.8.5 20150623 (Red Hat 4.8.5-39), 64-bit
            // The releases without the -1 suffix has a dyld/dylib issue on MacOs (e.g. the 11.7.0 version won't work)
            enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:11.6.0-1"),
        )
        testImplementation("io.zonky.test:embedded-postgres:1.3.1")

        constraints {
            implementation("org.apache.commons:commons-compress") {
                because("org.apache.avro:avro:1.10.2 -> https://snyk.io/vuln/SNYK-JAVA-ORGAPACHECOMMONS-1316641")
                version {
                    require("1.21")
                }
            }
            implementation("org.postgresql:postgresql") {
                because("no.nav:vault-jdbc@1.3.7 -> https://app.snyk.io/vuln/SNYK-JAVA-ORGPOSTGRESQL-571481")
                version {
                    require("42.2.23")
                }
            }
            implementation("commons-collections:commons-collections") {
                because("org.apache.cxf:cxf-rt-ws-security@3.4.4 -> https://app.snyk.io/vuln/SNYK-JAVA-COMMONSCOLLECTIONS-30078 and https://snyk.io/vuln/SNYK-JAVA-COMMONSCOLLECTIONS-472711")
                version {
                    require("3.2.2")
                }
            }
            implementation("org.eclipse.jetty:jetty-server") {
                because("no.nav:kafka-embedded-env@2.8.0 -> https://snyk.io/vuln/SNYK-JAVA-ORGECLIPSEJETTY-1313686")
                version {
                    require("9.4.43.v20210629")
                }
            }
            implementation("org.apache.santuario:xmlsec") {
                because("org.apache.cxf:cxf-rt-frontend-jaxws:3.4.4 and org.apache.cxf:cxf-rt-ws-security:3.4.4 -> https://app.snyk.io/vuln/SNYK-JAVA-ORGAPACHESANTUARIO-1655558")
                version {
                    require("2.2.3")
                }
            }
            implementation("io.netty:netty-codec") {
                because("io.ktor:ktor-server-netty@1.6.3 -> https://app.snyk.io/vuln/SNYK-JAVA-IONETTY-1584063 and https://app.snyk.io/vuln/SNYK-JAVA-IONETTY-1584064")
                version {
                    require("4.1.68.Final")
                }
            }
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "16"
            freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
            allWarningsAsErrors = true
        }
    }

    tasks.withType<Wrapper> {
        gradleVersion = "7.2"
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
        failFast = true
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
        failFast = true
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
