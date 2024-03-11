dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))
    implementation(project(":dokument:domain"))

    implementation(project(":vedtak:domain"))
    implementation(project(":behandling:common:domain"))
    implementation(project(":behandling:søknadsbehandling:domain"))

    implementation(project(":person:domain"))
    implementation(project(":oppgave:domain"))

    implementation(project(":satser"))
    implementation(project(":økonomi:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vedtak-application")
}

tasks.test {
    useJUnitPlatform()
}
