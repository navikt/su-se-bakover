dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":utenlandsopphold:domain"))
    implementation(project(":institusjonsopphold:domain"))
    implementation(project(":oppgave:domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":dokument:domain"))

    testImplementation(project(":test-common"))
}
