dependencies {
    implementation(project(":dokument:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("dokument-database")
}
