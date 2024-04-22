tasks.named<Jar>("jar") {
    archiveBaseName.set("app")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "no.nav.su.se.bakover.datapakker.fritekstAvslag.AppKt"
        attributes["Class-Path"] =
            configurations.runtimeClasspath.get().joinToString(separator = " ") {
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

dependencies {
    implementation(platform(rootProject.libs.google.cloud.bom))
    implementation(rootProject.libs.google.cloud.bigquery)
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
}
