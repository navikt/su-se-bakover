buildscript {
    dependencies {
        classpath("org.ajoberstar:grgit:2.3.0")
    }
}

plugins {
    kotlin("jvm") version "1.5.21"
    // Støtter unicode filer (i motsetning til https://github.com/JLLeitschuh/ktlint-gradle 10.0.0) og har nyere dependencies som gradle. Virker som den oppdateres hyppigere.
    id("org.jmailen.kotlinter") version "3.5.1"
    id("com.github.ben-manes.versions") version "0.39.0" // Finds latest versions
    id("se.patrikerdes.use-latest-versions") version "0.2.17"
}

version = "0.0.1"

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jmailen.kotlinter")
    apply(plugin = "com.github.ben-manes.versions")
    apply(plugin = "se.patrikerdes.use-latest-versions")
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/releases/")
        maven("https://packages.confluent.io/maven/")
    }
    val junitJupiterVersion = "5.7.2"
    val arrowVersion = "0.13.2"
    val kotestVersion = "4.6.1"
    val jacksonVersion = "2.12.5"
    val kotlinVersion = "1.5.21"
    dependencies {
        api(kotlin("stdlib-jdk8"))

        implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
        implementation("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
        implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")
        implementation("io.arrow-kt:arrow-core:$arrowVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
        implementation("ch.qos.logback:logback-classic:1.2.5")
        implementation("net.logstash.logback:logstash-logback-encoder:6.6")
        implementation("io.github.cdimascio:dotenv-kotlin:6.2.2")
        implementation("org.apache.kafka:kafka-clients:2.8.0")
        implementation("com.networknt:json-schema-validator:1.0.58")
        implementation("no.finn.unleash:unleash-client-java:4.4.0")

        implementation("com.ibm.mq:com.ibm.mq.allclient:9.2.3.0")
        implementation("io.confluent:kafka-avro-serializer:6.2.0")
        implementation("org.apache.avro:avro:1.10.2")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
        testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.0.3")
        testImplementation("io.kotest:kotest-extensions:$kotestVersion")
        testImplementation("org.skyscreamer:jsonassert:1.5.0")
        testImplementation("org.mockito.kotlin:mockito-kotlin:3.2.0")

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
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "16"
            freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
            allWarningsAsErrors = true
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            // We only want to log failed and skipped tests when running Gradle.
            events("skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    tasks.withType<Wrapper> {
        gradleVersion = "7.2"
    }
    // https://github.com/ben-manes/gradle-versions-plugin
    tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
        fun isNonStable(version: String): Boolean {
            val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
            val regex = "^[0-9,.v-]+(-r)?$".toRegex()
            val isStable = stableKeyword || regex.matches(version)
            return isStable.not()
        }
        rejectVersionIf {
            isNonStable(candidate.version) && !isNonStable(currentVersion)
        }
        checkForGradleUpdate = true
        gradleReleaseChannel = "current"
        outputFormatter = "json"
        outputDir = "build/dependencyUpdates"
        reportfileName = "report"
        revision = "release" // Not waterproof
    }

    // Run `./gradlew allDeps` to get a dependency graph
    task("allDeps", DependencyReportTask::class) {}
}

tasks.check {
    // Må ligge på root nivå
    dependsOn("installKotlinterPrePushHook")
}
