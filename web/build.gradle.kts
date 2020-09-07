repositories {
    maven("https://dl.bintray.com/kotlin/ktor")
}

val ktorVersion = "1.3.1"
val orgJsonVersion = "20200518"
val micrometerRegistryPrometheusVersion = "1.5.3"

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":database"))
    implementation(project(":client"))
    testImplementation(project(":database", "testArchives"))

    implementation("org.json:json:$orgJsonVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryPrometheusVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.10.1")
    implementation("com.ibm.mq:com.ibm.mq.allclient:9.1.5.0")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "junit")
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
    }
    testImplementation("com.opentable.components:otj-pg-embedded:0.13.3")
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
