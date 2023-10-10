dependencies {
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("dokument-application")
}
