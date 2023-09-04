description = "Ansvaret for å bygge main jar og kjøre applikasjonen i produksjon"

dependencies {
    implementation(project(":web"))
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    testImplementation(project(":client"))
    testImplementation(project(":domain"))
    testImplementation(project(":database"))
    testImplementation(project(":test-common"))
    testImplementation(project(":web-regresjonstest"))
}
tasks.named<Jar>("jar") {
    archiveBaseName.set("app")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "no.nav.su.se.bakover.application.ApplicationKt"
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
            it.name
        }
    }
    doLast {
        configurations.runtimeClasspath.get().forEach {
            val fileProvider: Provider<RegularFile> = layout.buildDirectory.file("libs/${it.name}")
            val targetFile = File(fileProvider.get().toString())
            if (!targetFile.exists()) {
                it.copyTo(targetFile)
            }
        }
    }
}
