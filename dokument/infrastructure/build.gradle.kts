dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation(project(":dokument:domain"))
    implementation(project(":dokument:application"))

    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("dokument-infrastructure")
}
