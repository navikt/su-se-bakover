dependencies {
    implementation(project(":common:domain"))
    implementation(project(":kontrollsamtale:domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":person:domain"))
    implementation(project(":oppgave:domain"))
    implementation(project(":vedtak:domain"))
    implementation(project(":behandling:common:domain"))

    testImplementation(project(":test-common"))
    testImplementation(project(":vilkår:utenlandsopphold:domain"))
    testImplementation(project(":hendelse:domain"))
    testImplementation(project(":økonomi:domain"))
    testImplementation(project(":tilbakekreving:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("kontrollsamtale-application")
}
