buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath( "org.ajoberstar:grgit:1.1.0")
    }
}
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
}

version = "0.0.1"
apply(plugin = "org.jetbrains.kotlin.jvm")
java {
    sourceCompatibility = JavaVersion.VERSION_12
    targetCompatibility = JavaVersion.VERSION_12
}

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
val junitJupiterVersion = "5.6.0-M1"
val fuelVersion = "2.2.1"
val wireMockVersion = "2.23.2"
val orgJsonVersion = "20180813"
val micrometerRegistryPrometheusVersion = "1.3.2"
val flywayVersion = "6.2.1"
val hikariVersion = "3.3.1"
val vaultJdbcVersion = "1.3.1"
val kotliqueryVersion = "1.3.1"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("net.logstash.logback:logstash-logback-encoder:5.2")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-gson:$fuelVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("org.json:json:$orgJsonVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryPrometheusVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation ("io.ktor:ktor-gson:$ktorVersion")
    implementation("org.apache.kafka:kafka-streams:2.3.0")
    implementation("no.nav:su-meldinger:7e1f3d035dd830764448cdd7b288110dda211d61")

    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "junit")
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testImplementation("com.github.tomakehurst:wiremock:$wireMockVersion") {
        exclude(group = "junit")
    }
    testImplementation("com.opentable.components:otj-pg-embedded:0.13.3")
    testImplementation("no.nav:kafka-embedded-env:2.2.3")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("app")
    archiveVersion.set("")
    dependsOn.add("generateVersion")
    manifest {
        attributes["Main-Class"] = "no.nav.su.se.bakover.ApplicationKt"
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

tasks {
    val generateVersion by registering(VersionTask::class) {
        val grgit = org.ajoberstar.grgit.Grgit.open(project.rootDir)
        val currentCommit = grgit.head().id
        version.set(currentCommit)
        rootFile.set(File("${buildDir.absolutePath}/resources/main"))
    }
    val jar by existing
    jar.get().dependsOn ( generateVersion )
}

open class VersionTask(): DefaultTask() {
    val version: Property<String> = project.objects.property()
    val rootFile: Property<File> = project.objects.property()
    @TaskAction
    fun writeGitSHAToFile() {
        val versionFile = File(rootFile.get(), "VERSION")
        if (versionFile.exists()) versionFile.delete()
        versionFile.createNewFile()
        versionFile.writeText("commit.sha=${version.get()}")
    }
}
