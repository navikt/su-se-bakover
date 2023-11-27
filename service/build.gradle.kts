dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))
    implementation(project(":client"))
    implementation(project(":statistikk"))
    implementation(project(":økonomi:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":oppgave:domain"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":person:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":sats"))

    testImplementation(project(":vilkår:utenlandsopphold:domain"))
    testImplementation(project(":test-common"))
}
