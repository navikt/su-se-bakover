dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))
    implementation(project(":dokument:domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("kontrollsamtale-domain")
}
