// Inneholder regresjonstester for web-laget (black-box asserting).
// Separert til sin egen modul for å kunne bygges parallelt med de andre testene på byggserveren.
val ktorVersion: String by project
dependencies {
    testImplementation(project(":domain"))
    testImplementation(project(":database"))
    testImplementation(project(":web"))
    testImplementation(project(":client"))
    testImplementation(project(":common"))
    testImplementation(project(":service"))
    testImplementation(project(":test-common"))
    testImplementation(project(":kontrollsamtale:domain"))
    testImplementation(project(":kontrollsamtale:application"))
    testImplementation(project(":kontrollsamtale:infrastructure"))

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "junit")
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
        exclude(group = "org.eclipse.jetty.http2") // conflicts with WireMock
    }
}

task<JavaExec>("nySøknad") {
    classpath = project(":web-regresjonstest").sourceSets["test"].runtimeClasspath
    mainClass.set("no.nav.su.se.bakover.web.søknad.ny.OpprettNySakMedSøknadLokaltKt")
}

task<JavaExec>("nySøknadsbehandling") {
    classpath = project(":web-regresjonstest").sourceSets["test"].runtimeClasspath
    mainClass.set("no.nav.su.se.bakover.web.søknadsbehandling.OpprettSakMedSøknadOgSøknadsbehandlingLokaltKt")
}
