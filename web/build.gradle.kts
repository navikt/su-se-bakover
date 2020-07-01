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

val ktorVersion = "1.2.6"
val orgJsonVersion = "20180813"
val micrometerRegistryPrometheusVersion = "1.3.2"

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":database"))
    implementation(project(":client"))
    testImplementation(project(":database", "testArchives"))

    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("net.logstash.logback:logstash-logback-encoder:5.2")
    implementation("org.json:json:$orgJsonVersion")
    implementation("no.nav:su-meldinger")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryPrometheusVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.11.1")

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
