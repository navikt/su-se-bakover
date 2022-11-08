dependencies {
    implementation(project(":common"))
    implementation(project(":database"))
    implementation(project(":domain"))
    implementation(project(":kontrollsamtale:application"))
    implementation(project(":kontrollsamtale:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("kontrollsamtale-infrastructure")
}
