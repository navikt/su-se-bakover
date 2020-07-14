val fuelVersion = "2.2.1"
val wireMockVersion = "2.23.2"
val orgJsonVersion = "20180813"
val tjenestespesifikasjonVersion = "1.2020.07.06-13.56-22258ab2afe2"

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))

    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("net.logstash.logback:logstash-logback-encoder:5.2")
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-gson:$fuelVersion")

    implementation("org.json:json:$orgJsonVersion")

    implementation("no.nav.tjenestespesifikasjoner:nav-virksomhet-oppdragsbehandling-v1-meldingsdefinisjon:$tjenestespesifikasjonVersion")
    implementation("no.nav.tjenestespesifikasjoner:nav-system-os-simuler-fp-service-tjenestespesifikasjon:$tjenestespesifikasjonVersion")

    testImplementation("com.github.tomakehurst:wiremock:$wireMockVersion") {
        exclude(group = "junit")
    }
}
