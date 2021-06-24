repositories {
    maven("https://dl.bintray.com/kotlin/ktor")
}

val ktorVersion = "1.6.0"
val orgJsonVersion = "20210307"
val micrometerRegistryPrometheusVersion = "1.7.0"

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":service"))
    implementation(project(":database"))
    implementation(project(":client"))

    implementation("org.json:json:$orgJsonVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }
    implementation("io.ktor:ktor-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryPrometheusVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.12.3")
    implementation("com.papertrailapp", "logback-syslog4j", "1.0.0")


    testImplementation(project(":database", "testArchives"))
    testImplementation(project(":test-common"))
    testImplementation("org.xmlunit:xmlunit-matchers:2.8.2")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "junit")
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
        exclude(group = "org.eclipse.jetty.http2") // conflicts with WireMock
    }
    testImplementation("com.opentable.components:otj-pg-embedded:0.13.4") {
        exclude(group = "com.github.spotbugs")
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
