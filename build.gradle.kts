buildscript {
    dependencies {
        classpath("org.ajoberstar:grgit:2.3.0")
    }
}

plugins {
    kotlin("jvm") version "1.5.20"
    // Støtter unicode filer (i motsetning til https://github.com/JLLeitschuh/ktlint-gradle 10.0.0) og har nyere dependencies som gradle. Virker som den oppdateres hyppigere.
    id("org.jmailen.kotlinter") version "3.4.5"
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
    }
    val junitJupiterVersion = "5.7.2"
    val arrowVersion = "0.13.2"
    val kotestVersion = "4.6.1"
    val jacksonVersion = "2.12.4"
    val bouncycastleVersion = "1.69"
    val kotlinVersion = "1.5.20"
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
        implementation("com.networknt:json-schema-validator:1.0.57")
        implementation("no.finn.unleash:unleash-client-java:4.4.0")

        implementation("com.ibm.mq:com.ibm.mq.allclient:9.2.3.0")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
        testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.0.2")
        testImplementation("io.kotest:kotest-extensions:$kotestVersion")
        testImplementation("org.skyscreamer:jsonassert:1.5.0")
        testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
        testImplementation("org.mockito:mockito-core:3.11.2")

        constraints {
            implementation("io.netty:netty-codec-http2:4.1.66.Final") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-IONETTY-1020439")
            }
            implementation("commons-collections:commons-collections:3.2.2") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-COMMONSCOLLECTIONS-30078")
            }
            implementation("commons-codec:commons-codec:1.15") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-COMMONSCODEC-561518")
            }
            implementation("com.google.guava:guava:30.1.1-jre") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-COMGOOGLEGUAVA-1015415")
            }
            implementation("org.apache.httpcomponents:httpclient:4.5.13") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-ORGAPACHEHTTPCOMPONENTS-1016906")
            }
            implementation("org.postgresql:postgresql:42.2.22") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-ORGPOSTGRESQL-571481")
            }
            implementation("org.apache.cxf:cxf-rt-transports-http:3.4.4") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-ORGAPACHECXF-1039798")
            }
            implementation("junit:junit:4.13.2") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-JUNIT-1017047")
            }
            implementation("org.bouncycastle:bcprov-jdk15on:$bouncycastleVersion") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-ORGBOUNCYCASTLE-1052448")
            }
            implementation("org.bouncycastle:bcpkix-jdk15on:$bouncycastleVersion") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-ORGBOUNCYCASTLE-1052448")
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
        gradleVersion = "7.0.2"
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
}

tasks.check {
    // Må ligge på root nivå
    dependsOn("installKotlinterPrePushHook")
}
