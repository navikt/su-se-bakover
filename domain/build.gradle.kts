dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":vilkår:utenlandsopphold:domain"))
    implementation(project(":vilkår:institusjonsopphold:domain"))
    implementation(project(":oppgave:domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":person:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":sats"))

    testImplementation(project(":test-common"))
}
