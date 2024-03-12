dependencies {
    implementation(project(":økonomi:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("behandling-regulering-infrastructure")
}
