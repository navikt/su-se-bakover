dependencies {
    implementation(project(":tilbakekreving:application"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":tilbakekreving:infrastructure"))

    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":økonomi:domain"))

    implementation(project(":domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("tilbakekreving-presentation")
}

tasks.test {
    useJUnitPlatform()
}
