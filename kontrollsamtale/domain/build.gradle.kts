dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("kontrollsamtale-domain")
}
