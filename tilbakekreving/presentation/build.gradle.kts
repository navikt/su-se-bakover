dependencies {
    implementation(project(":tilbakekreving:application"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":tilbakekreving:infrastructure"))

    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":common:presentation"))

    implementation(project(":økonomi:domain"))
    implementation(project(":oppgave:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":hendelse:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":person:domain"))
    implementation(project(":vedtak:domain"))

    implementation(project(":domain"))
    implementation(project(":service"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("tilbakekreving-presentation")
}

tasks.test {
    useJUnitPlatform()
}
