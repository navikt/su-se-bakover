dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":common:infrastructure"))

    implementation(project(":dokument:application"))
    implementation(project(":dokument:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("dokument-presentation")
}
