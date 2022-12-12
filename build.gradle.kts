import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm")
    id("com.diffplug.spotless") version "6.12.0"
}

version = "0.0.1"
val ktorVersion: String by project

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.diffplug.spotless")
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/releases/")
        maven("https://packages.confluent.io/maven/")
    }
    val junitJupiterVersion = "5.9.1"
    val kotestVersion = "5.5.4"
    val jacksonVersion = "2.14.1"
    val kotlinVersion: String by this
    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
        implementation("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
        implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
        implementation(platform("io.arrow-kt:arrow-stack:1.1.3"))
        implementation("io.arrow-kt:arrow-core")
        implementation("io.arrow-kt:arrow-fx-coroutines")
        implementation("io.arrow-kt:arrow-fx-stm")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
        implementation("ch.qos.logback:logback-classic:1.4.5")
        implementation("net.logstash.logback:logstash-logback-encoder:7.2")
        implementation("io.github.cdimascio:dotenv-kotlin:6.4.0")
        implementation("org.apache.kafka:kafka-clients:3.3.1")
        implementation("com.networknt:json-schema-validator:1.0.75")
        implementation("no.finn.unleash:unleash-client-java:4.4.1")

        implementation("com.ibm.mq:com.ibm.mq.allclient:9.3.1.0")
        implementation("io.confluent:kafka-avro-serializer:7.1.3")
        implementation("org.apache.avro:avro:1.11.1")
        implementation("com.github.ben-manes.caffeine:caffeine:3.1.2")
        implementation("io.micrometer:micrometer-core:1.10.2")
        implementation("io.micrometer:micrometer-registry-prometheus:1.10.2")
        implementation("com.github.seratch:kotliquery:1.9.0")
        implementation("org.flywaydb:flyway-core:9.9.0")
        implementation("com.zaxxer:HikariCP:5.0.1")
        implementation("com.github.navikt:vault-jdbc:1.3.10")

        implementation("io.ktor:ktor-server-netty:$ktorVersion")
        implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion") {
            exclude(group = "junit")
        }
        implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
        implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
        implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-server-call-id:$ktorVersion")
        implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
        implementation("io.ktor:ktor-server-forwarded-header:$ktorVersion")
        implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
        implementation("com.papertrailapp", "logback-syslog4j", "1.0.0")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
        testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.3.0")
        testImplementation("io.kotest:kotest-extensions:$kotestVersion")
        testImplementation("org.skyscreamer:jsonassert:1.5.1")
        testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
        // Embedded database brukes av modulene: web og database
        testImplementation(
            // select version() i preprod 2022-08-30 -> PostgreSQL 11.16 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 4.8.5 20150623 (Red Hat 4.8.5-44), 64-bit
            // Merk at det ikke har blitt kompilert så mange drivere for denne: https://mvnrepository.com/artifact/io.zonky.test.postgres/embedded-postgres-binaries-darwin-arm64v8 og kun en versjon for postgres 11
            enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:11.15.0"),
        )
        testImplementation("io.zonky.test:embedded-postgres:2.0.2")
        // Legger til manglende binaries for nye Mac's med M1 cpuer.
        testImplementation("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8")
        testImplementation("org.xmlunit:xmlunit-matchers:2.9.0")
        testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
            exclude(group = "junit")
            exclude(group = "org.eclipse.jetty") // conflicts with WireMock
            exclude(group = "org.eclipse.jetty.http2") // conflicts with WireMock
        }

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
            freeCompilerArgs += "-progressive"
            allWarningsAsErrors = true
        }
    }

    java {
        // Ensuring any java-files is also compiled with the preferred version.
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }

    tasks.withType<Wrapper> {
        gradleVersion = "7.5.1"
    }

    // Run `./gradlew allDeps` to get a dependency graph
    task("allDeps", DependencyReportTask::class) {}

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            /** spotless støtter ikke .editorconfig enda så vi må duplisere den her :( */
            ktlint("0.47.1").editorConfigOverride(
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

configure(listOf(project(":client"))) {
// :client er vanskelig å parallellisere så lenge den bruker Wiremock på en statisk måte. Samtidig gir det ikke så mye mening siden testene er raske og ikke? feiler på timing issues.
    tasks.test {
        sharedTestSetup()
    }
}

configure(listOf(project(":web-kafka-test"))) {
    // :web-kafka-test denne gir veldig ofte timing issues. Prøver å kjøre denne ikke-parallellt.
    tasks.test {
        sharedTestSetup()
        skipHeavyInfrastructureTestsIfToggled(this)
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
        project(":statistikk"),
        project(":hendelse:infrastructure"),
        project(":hendelse:domain"),
        project(":utenlandsopphold:application"),
        project(":utenlandsopphold:infrastructure"),
        project(":utenlandsopphold:domain"),
    ),
) {
    tasks.test {
        sharedTestSetup()
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
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
    }
}

fun Project.skipHeavyInfrastructureTestsIfToggled(test: Test) {
    if (findProperty("skip-heavy-infrastructure-tests") == "true") {
        println("Skipping heavy infrastructure tests like Kafka.")
        test.filter {
            excludeTestsMatching("*KafkaTest*")
            isFailOnNoMatchingTests = false
        }
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
