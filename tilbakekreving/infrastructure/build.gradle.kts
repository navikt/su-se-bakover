dependencies {
    implementation(project(":domain"))
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:database"))
    implementation(project(":hendelse:domain"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":tilbakekreving:application"))

    implementation(project(":common:infrastructure"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":hendelse:domain"))
    implementation(project(":økonomi:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("tilbakekreving-infrastructure")
}

tasks.test {
    useJUnitPlatform()
}
