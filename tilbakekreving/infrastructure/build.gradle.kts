dependencies {
    implementation(project(":domain"))
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))
    implementation(project(":hendelse:domain"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":behandling:domain"))

    implementation(project(":common:infrastructure"))
    implementation(project(":common:infrastructure:cxf"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":hendelse:domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":oppgave:domain"))

    implementation(rootProject.libs.tjenestespesifikasjoner.tilbakekreving)

    implementation(rootProject.libs.cxf.rt.features.logging)
    implementation(rootProject.libs.cxf.rt.frontend.jaxws)
    implementation(rootProject.libs.cxf.rt.transports.http)
    implementation(rootProject.libs.cxf.rt.ws.security) {
        // https://security.snyk.io/vuln/SNYK-JAVA-ORGAPACHEVELOCITY-3116414
        exclude(group = "org.apache.velocity")
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }

    testImplementation(project(":test-common"))
    testImplementation(project(":satser"))
    // We exclude jdk15on because of security issues. We use jdk18on instead.
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("tilbakekreving-infrastructure")
}

tasks.test {
    useJUnitPlatform()
}
