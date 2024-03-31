dependencies {
    implementation(project(":common:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("behandling-klage-domain")
}
