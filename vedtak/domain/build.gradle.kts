dependencies {
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vedtak-domain")
}

tasks.test {
    useJUnitPlatform()
}
