dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))

    implementation(project(":vedtak:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":behandling:søknadsbehandling:domain"))

    implementation(project(":person:domain"))
    implementation(project(":oppgave:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vedtak-application")
}

tasks.test {
    useJUnitPlatform()
}
