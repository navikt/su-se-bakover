dependencies {
    implementation(project(":common"))
    testImplementation(project(":domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("hendelse-application")
}
