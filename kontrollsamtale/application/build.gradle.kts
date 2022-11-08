dependencies {
    implementation(project(":common"))
    implementation(project(":kontrollsamtale:domain"))
    implementation(project(":domain"))
    testImplementation(project(":test-common"))
    // Mockito
    testImplementation(project(":utenlandsopphold:domain"))
    testImplementation(project(":hendelse:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("kontrollsamtale-application")
}
