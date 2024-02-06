dependencies {
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":behandling:søknadsbehandling:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vedtak-domain")
}

tasks.test {
    useJUnitPlatform()
}
