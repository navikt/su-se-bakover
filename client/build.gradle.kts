val fuelVersion = "2.3.1"
val wireMockVersion = "2.30.1"
val orgJsonVersion = "20210307"
val tjenestespesifikasjonVersion = "2567.324e61f"
val cxfVersion = "3.4.4"
val jettyVersion = "9.4.42.v20210604"

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))

    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-gson:$fuelVersion")
    implementation("javax.xml.bind:jaxb-api:2.3.1")

    implementation("org.json:json:$orgJsonVersion")

    implementation("com.github.navikt.tjenestespesifikasjoner:nav-virksomhet-oppdragsbehandling-v1-meldingsdefinisjon:$tjenestespesifikasjonVersion")
    implementation("com.github.navikt.tjenestespesifikasjoner:nav-system-os-simuler-fp-service-tjenestespesifikasjon:$tjenestespesifikasjonVersion")
    implementation("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")
    implementation("javax.xml.ws:jaxws-api:2.3.1")
    implementation("javax.jws:javax.jws-api:1.1")
    // Fails to find SAAJMetaFactoryImpl when either missing or if you upgrade to 2.0.0
    implementation("com.sun.xml.messaging.saaj:saaj-impl:1.5.2")

    testImplementation(project(":test-common"))
    testImplementation("com.github.tomakehurst:wiremock-jre8:$wireMockVersion") {
        exclude(group = "junit")
    }
    testImplementation("org.xmlunit:xmlunit-matchers:2.8.2")
}

configure<SourceSetContainer> {
    named("main") {
        java.srcDir("src/main/java")
        java.srcDir("$buildDir/generated-sources/xsd2java")
        java.srcDir("$buildDir/generated-sources/wsdl2java")
    }
}
