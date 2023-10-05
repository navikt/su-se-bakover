dependencies {
    implementation(project(":common:domain"))
    implementation(project(":kontrollsamtale:domain"))
    implementation(project(":domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":person:domain"))

    testImplementation(project(":test-common"))
    // Mockito
    testImplementation(project(":utenlandsopphold:domain"))
    testImplementation(project(":hendelse:domain"))
    testImplementation(project(":økonomi:domain"))
    testImplementation(project(":tilbakekreving:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("kontrollsamtale-application")
}
