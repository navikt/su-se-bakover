dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":utenlandsopphold:domain"))
    implementation(project(":utenlandsopphold:infrastructure"))

    testImplementation(project(":test-common"))
}
