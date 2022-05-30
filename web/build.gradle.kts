val ktorVersion: String by project

plugins {
    /** Det ser ut som disse genererte filene ikke blir ekskludert av ktlint-tasken.
     * Gradle gir oss noen warnings om at vi ikke kan oppdatere til Gradle 8. */
    id("com.github.davidmc24.gradle.plugin.avro") version "1.3.0"
}

avro {
    isGettersReturnOptional.set(true)
    isOptionalGettersForNullableFieldsOnly.set(true)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":service"))
    implementation(project(":database"))
    implementation(project(":client"))
    implementation(project(":test-common"))

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

    testImplementation(project(":database", "testArchives"))
    testImplementation(project(":test-common"))
    testImplementation("org.xmlunit:xmlunit-matchers:2.9.0")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "junit")
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
        exclude(group = "org.eclipse.jetty.http2") // conflicts with WireMock
    }
    testImplementation("no.nav:kafka-embedded-env:3.1.6") {
        // Breaks build: exclude(group = "org.glassfish.jersey.ext", module = "jersey-bean-validation")
        // Breaks build: exclude(group = "org.glassfish", module = "jakarta.el")
        // Breaks build: exclude(group = "org.eclipse.jetty", module = "jetty-server")
        // Breaks build: exclude(group = "org.eclipse.jetty", module = "jetty-servlets")
        exclude(group = "org.eclipse.jetty", module = "jetty-webapp")
        exclude(group = "log4j") // module = "log4j"
        exclude(group = "io.netty") // module = "netty-handler"
        exclude(group = "io.grpc") // module = "grpc-core"
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("app")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "no.nav.su.se.bakover.web.ApplicationKt"
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
            it.name
        }
    }
    doLast {
        configurations.runtimeClasspath.get().forEach {
            val file = File("$buildDir/libs/${it.name}")
            if (!file.exists())
                it.copyTo(file)
        }
    }
}
