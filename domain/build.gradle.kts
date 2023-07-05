dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":utenlandsopphold:domain"))
    implementation(project(":institusjonsopphold:domain"))
    implementation(project(":økonomi:domain"))

    testImplementation(project(":test-common"))
}
