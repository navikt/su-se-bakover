dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":database"))
    implementation(project(":domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:presentation"))
    implementation(project(":kontrollsamtale:application"))
    implementation(project(":kontrollsamtale:domain"))
    implementation(project(":person:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("kontrollsamtale-infrastructure")
}
