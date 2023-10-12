dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation(project(":dokument:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("dokument-infrastructure")
}
