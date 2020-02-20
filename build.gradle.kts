plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
}

allprojects {
    version = "0.0.1"
    apply(plugin = "org.jetbrains.kotlin.jvm")
    java {
        sourceCompatibility = JavaVersion.VERSION_12
        targetCompatibility = JavaVersion.VERSION_12
    }

    repositories {
        jcenter()
        maven("https://dl.bintray.com/kotlin/ktor")
        maven("http://packages.confluent.io/maven/")
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
        gradleVersion = "6.0.1"
    }


}



