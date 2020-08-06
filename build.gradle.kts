buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.ajoberstar:grgit:2.3.0")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
    id("org.jlleitschuh.gradle.ktlint") version "9.3.0"
    id("com.github.ben-manes.versions") version "0.29.0" // Finds latest versions
    id("se.patrikerdes.use-latest-versions") version "0.2.14"
}

version = "0.0.1"

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "com.github.ben-manes.versions")
    apply(plugin = "se.patrikerdes.use-latest-versions")
    repositories {
        jcenter()
        maven("https://dl.bintray.com/kotlin/ktor")
        maven("https://packages.confluent.io/maven/")
    }
    val junitJupiterVersion = "5.6.2"
    val arrowVersion = "0.10.5"
    val kotestVersion = "4.1.3"
    val jacksonVersion = "2.11.1"
    dependencies {
        api(kotlin("stdlib-jdk8"))

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.8")
        implementation("io.arrow-kt:arrow-core:$arrowVersion")
        implementation("io.arrow-kt:arrow-syntax:$arrowVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("ch.qos.logback:logback-classic:1.2.3")
        implementation("net.logstash.logback:logstash-logback-encoder:6.4")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-json-jvm:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-arrow-jvm:$kotestVersion")
        testImplementation("org.skyscreamer:jsonassert:1.5.0")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "12"
            freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
            allWarningsAsErrors = true
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    tasks.withType<Wrapper> {
        gradleVersion = "6.2.2"
    }

    tasks.named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java).configure {
        checkForGradleUpdate = true
        gradleReleaseChannel = "current"
        outputFormatter = "json"
        outputDir = "build/dependencyUpdates"
        reportfileName = "report"
        revision = "release" // Not waterproof
    }
}
