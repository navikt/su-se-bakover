buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.ajoberstar:grgit:1.1.0")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
}

version = "0.0.1"

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    val githubUser: String? by project
    val githubPassword: String? by project
    repositories {
        jcenter()
        maven("https://dl.bintray.com/kotlin/ktor")
        maven("http://packages.confluent.io/maven/")
    }
    val junitJupiterVersion = "5.6.0-M1"
    val arrowVersion = "0.10.5"
    val kotestVersion = "4.1.1"
    val jacksonVersion = "2.11.1"
    dependencies {
        api(kotlin("stdlib-jdk8"))

        implementation("io.arrow-kt:arrow-core:$arrowVersion")
        implementation("io.arrow-kt:arrow-syntax:$arrowVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-json-jvm:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-arrow-jvm:$kotestVersion")
        testImplementation("org.skyscreamer:jsonassert:1.5.0")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "12"
    }

    tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
        kotlinOptions.jvmTarget = "12"
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
}
