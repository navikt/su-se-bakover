plugins {
    id("com.github.davidmc24.gradle.plugin.avro") version "1.5.0"
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
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":utenlandsopphold:presentation"))
    implementation(project(":utenlandsopphold:application"))
    implementation(project(":utenlandsopphold:domain"))
    implementation(project(":utenlandsopphold:infrastructure"))
    implementation(project(":test-common"))

    testImplementation(project(":test-common"))
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
            if (!file.exists()) {
                it.copyTo(file)
            }
        }
    }
}

// Pluginen burde sette opp dette selv, men den virker discontinued.
tasks.named("compileKotlin").get().dependsOn(":web:generateAvroJava")
tasks.named("compileTestKotlin").get().dependsOn(":web:generateTestAvroJava")
