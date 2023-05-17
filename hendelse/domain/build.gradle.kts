dependencies {
    implementation(project(":common:domain"))
    testImplementation(project(":domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("hendelse-domain")
}
