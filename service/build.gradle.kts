dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))
    implementation(project(":client"))
    implementation(project(":statistikk"))
    implementation(project(":økonomi:domain"))

    testImplementation(project(":hendelse:domain"))
    testImplementation(project(":utenlandsopphold:domain"))
    testImplementation(project(":test-common"))
}
