dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":client"))
    implementation(project(":statistikk"))

    testImplementation(project(":hendelse:domain"))
    testImplementation(project(":utenlandsopphold:domain"))
    testImplementation(project(":test-common"))
}
