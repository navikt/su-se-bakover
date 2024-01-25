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
    implementation(project(":vedtak:domain"))
    implementation(project(":vedtak:application"))
    implementation(project(":person:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":satser"))
    implementation(project(":vilkår:domain"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":vilkår:flyktning:domain"))
    implementation(project(":vilkår:fastopphold:domain"))
    implementation(project(":beregning"))

    testImplementation(project(":vilkår:utenlandsopphold:domain"))
    testImplementation(project(":test-common"))
}
