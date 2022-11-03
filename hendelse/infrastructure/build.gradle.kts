dependencies {
    implementation(project(":common"))
    implementation(project(":hendelse:domain"))

    testImplementation(project(":domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("hendelse-infrastructure")
}
