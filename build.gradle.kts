buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath( "org.ajoberstar:grgit:1.1.0")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
}

version = "0.0.1"
apply(plugin = "org.jetbrains.kotlin.jvm")
java {
    sourceCompatibility = JavaVersion.VERSION_12
    targetCompatibility = JavaVersion.VERSION_12
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    val githubUser: String? by project
    val githubPassword: String? by project
    repositories {
        jcenter()
        maven("https://dl.bintray.com/kotlin/ktor")
        maven("http://packages.confluent.io/maven/")
        maven {
            url = uri("https://maven.pkg.github.com/navikt/su-meldinger")
            credentials {
                username = githubUser
                password = githubPassword
            }
        }
    }
    val junitJupiterVersion = "5.6.0-M1"
    dependencies {
        api(kotlin("stdlib-jdk8"))

        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
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
        }
    }

    tasks.withType<Wrapper> {
        gradleVersion = "6.2.2"
    }


}
