dependencies {
    implementation(project(":common:domain"))
    implementation(project(":kontrollsamtale:domain"))
    implementation(project(":domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":person:domain"))
    implementation(project(":oppgave:domain"))

    testImplementation(project(":test-common"))
    // Mockito
    testImplementation(project(":vilkår:utenlandsopphold:domain"))
    testImplementation(project(":hendelse:domain"))
    testImplementation(project(":økonomi:domain"))
    testImplementation(project(":tilbakekreving:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("kontrollsamtale-application")
}
