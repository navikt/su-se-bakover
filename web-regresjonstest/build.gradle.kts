// Inneholder regresjonstester for web-laget (black-box asserting).
// Separert til sin egen modul for å kunne bygges parallelt med de andre testene på byggserveren.
val ktorVersion: String by project
dependencies {
    implementation(project(":domain"))
    implementation(project(":database"))
    implementation(project(":web"))
    implementation(project(":client"))
    implementation(project(":common"))
    implementation(project(":service"))
    implementation(project(":test-common"))
    implementation(project(":kontrollsamtale:domain"))
    implementation(project(":kontrollsamtale:application"))
    implementation(project(":kontrollsamtale:infrastructure"))

    implementation("io.ktor:ktor-client-java:$ktorVersion")
    implementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "junit")
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
        exclude(group = "org.eclipse.jetty.http2") // conflicts with WireMock
    }
    implementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
}

task<JavaExec>("nySøknad") {
    classpath = project(":web-regresjonstest").sourceSets["test"].runtimeClasspath
    mainClass.set("no.nav.su.se.bakover.web.søknad.ny.OpprettNySakMedSøknadLokaltKt")
}

task<JavaExec>("nySøknadsbehandling") {
    classpath = project(":web-regresjonstest").sourceSets["test"].runtimeClasspath
    mainClass.set("no.nav.su.se.bakover.web.søknadsbehandling.OpprettSakMedSøknadOgSøknadsbehandlingLokaltKt")
}
