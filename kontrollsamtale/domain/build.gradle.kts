dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":vedtak:domain"))
    // TODO jah: Try to remove this dependency
    implementation(project(":økonomi:domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("kontrollsamtale-domain")
}
