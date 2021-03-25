buildscript {
    dependencies {
        classpath("org.ajoberstar:grgit:2.3.0")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.31"
    id("org.jlleitschuh.gradle.ktlint") version "9.4.1"
    id("com.github.ben-manes.versions") version "0.36.0" // Finds latest versions
    id("se.patrikerdes.use-latest-versions") version "0.2.15"
}

version = "0.0.1"

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "com.github.ben-manes.versions")
    apply(plugin = "se.patrikerdes.use-latest-versions")
    repositories {
        mavenCentral()
        maven("http://packages.confluent.io/maven/")
        maven("https://jitpack.io")
    }
    val junitJupiterVersion = "5.7.1"
    val arrowVersion = "0.11.0"
    val kotestVersion = "4.4.1"
    val jacksonVersion = "2.12.1"
    val ktlintVersion = "0.41.0"
    val bouncycastleVersion = "1.68"
    dependencies {
        api(kotlin("stdlib-jdk8"))

        implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.31")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
        implementation("io.arrow-kt:arrow-core:$arrowVersion")
        implementation("io.arrow-kt:arrow-syntax:$arrowVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("ch.qos.logback:logback-classic:1.2.3")
        implementation("net.logstash.logback:logstash-logback-encoder:6.6")
        implementation("io.github.cdimascio:dotenv-kotlin:6.2.2")
        implementation("org.apache.kafka:kafka-clients:2.7.0")
        implementation("com.networknt:json-schema-validator:1.0.49")
        implementation("no.finn.unleash:unleash-client-java:4.1.0")

        // The 9.2.1.0 version fails to connect to the MQ server (hostname becomes null?)
        implementation("com.ibm.mq:com.ibm.mq.allclient:9.2.0.1")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-json-jvm:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-arrow-jvm:$kotestVersion")
        testImplementation("io.kotest:kotest-extensions-jvm:$kotestVersion")
        testImplementation("org.skyscreamer:jsonassert:1.5.0")
        testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
        testImplementation("org.mockito:mockito-core:3.7.7")

        constraints {
            implementation("io.netty:netty-codec-http2:4.1.53.Final") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-IONETTY-1020439")
            }
            implementation("commons-collections:commons-collections:3.2.2") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-COMMONSCOLLECTIONS-30078")
            }
            implementation("commons-codec:commons-codec:1.15") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-COMMONSCODEC-561518")
            }
            implementation("com.google.guava:guava:30.0-jre") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-COMGOOGLEGUAVA-1015415")
            }
            implementation("org.apache.httpcomponents:httpclient:4.5.13") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-ORGAPACHEHTTPCOMPONENTS-1016906")
            }
            implementation("org.postgresql:postgresql:42.2.13") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-ORGPOSTGRESQL-571481")
            }
            implementation("org.apache.cxf:cxf-rt-transports-http:3.4.2") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-ORGAPACHECXF-1039798")
            }
            implementation("junit:junit:4.13.1") {
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
            jvmTarget = "15"
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
        gradleVersion = "6.8.3"
    }

    tasks.named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java)
        .configure {
            checkForGradleUpdate = true
            gradleReleaseChannel = "current"
            outputFormatter = "json"
            outputDir = "build/dependencyUpdates"
            reportfileName = "report"
            revision = "release" // Not waterproof
        }

    ktlint {
        this.version.set(ktlintVersion)
    }
}
