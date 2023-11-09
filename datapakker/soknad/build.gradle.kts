tasks.named<Jar>("jar") {
    archiveBaseName.set("app")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "no.nav.su.se.bakover.datapakker.soknad.AppKt"
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
    implementation(platform("com.google.cloud:libraries-bom:26.27.0"))
    implementation("com.google.cloud:google-cloud-bigquery")
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
}
