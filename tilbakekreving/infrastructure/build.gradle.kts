dependencies {
    implementation(project(":domain"))
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))
    implementation(project(":hendelse:domain"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":behandling:common:domain"))

    implementation(project(":common:infrastructure"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":hendelse:domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":oppgave:domain"))

    testImplementation(project(":test-common"))
    testImplementation(project(":satser"))
    // We exclude jdk15on because of security issues. We use jdk18on instead.
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("tilbakekreving-infrastructure")
}

tasks.test {
    useJUnitPlatform()
}
