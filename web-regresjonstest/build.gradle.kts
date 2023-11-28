// Inneholder regresjonstester for web-laget (black-box asserting).
// Separert til sin egen modul for å kunne bygges parallelt med de andre testene på byggserveren.
dependencies {
    implementation(project(":domain"))
    implementation(project(":database"))
    implementation(project(":web"))
    implementation(project(":client"))
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":service"))
    implementation(project(":test-common"))
    implementation(project(":kontrollsamtale:domain"))
    implementation(project(":kontrollsamtale:application"))
    implementation(project(":kontrollsamtale:infrastructure"))
    implementation(project(":økonomi:infrastructure"))
    implementation(project(":økonomi:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:application"))
    implementation(project(":dokument:infrastructure"))

    implementation(project(":oppgave:domain"))

    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))

    implementation(project(":tilbakekreving:domain"))
    implementation(project(":tilbakekreving:presentation"))
    implementation(project(":tilbakekreving:application"))
    implementation(project(":tilbakekreving:infrastructure"))

    implementation(project(":person:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":grunnbeløp"))
    implementation(project(":satser"))
    implementation(project(":vilkår:domain"))
    implementation(project(":vilkår:formue:domain"))

    implementation(rootProject.libs.ktor.client.java)
    implementation(rootProject.libs.ktor.server.test.host) {
        exclude(group = "junit")
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
        exclude(group = "org.eclipse.jetty.http2") // conflicts with WireMock
    }
    implementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
}

task<JavaExec>("nySøknad") {
    classpath = project(":web-regresjonstest").sourceSets["test"].runtimeClasspath
    mainClass.set("no.nav.su.se.bakover.web.søknad.ny.OpprettNySakMedSøknadLokaltKt")
}

task<JavaExec>("nySøknadsbehandling") {
    classpath = project(":web-regresjonstest").sourceSets["test"].runtimeClasspath
    mainClass.set("no.nav.su.se.bakover.web.søknadsbehandling.OpprettSakMedSøknadOgSøknadsbehandlingLokaltKt")
}
