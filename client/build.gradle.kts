val fuelVersion = "2.3.0"
val wireMockVersion = "2.27.2"
val orgJsonVersion = "20201115"
val tjenestespesifikasjonVersion = "1.2020.10.07-08.40-90b3ab7bad15"
val cxfVersion = "3.4.1"
val jettyVersion = "9.4.35.v20201120"

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))

    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-gson:$fuelVersion")
    implementation("javax.xml.bind:jaxb-api:2.3.1")

    implementation("org.json:json:$orgJsonVersion")

    implementation("no.nav.tjenestespesifikasjoner:nav-virksomhet-oppdragsbehandling-v1-meldingsdefinisjon:$tjenestespesifikasjonVersion")
    implementation("no.nav.tjenestespesifikasjoner:nav-system-os-simuler-fp-service-tjenestespesifikasjon:$tjenestespesifikasjonVersion")
    implementation("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")
    implementation("javax.xml.ws:jaxws-api:2.3.1")
    implementation("javax.jws:javax.jws-api:1.1")
    implementation("com.sun.xml.messaging.saaj:saaj-impl:2.0.0")
    implementation("com.ibm.mq:com.ibm.mq.allclient:9.2.0.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.12.0")

    implementation(enforcedPlatform("org.eclipse.jetty:jetty-bom:$jettyVersion")) {
        because("https://app.snyk.io/vuln/SNYK-JAVA-JUNIT-1017047")
    }
    testImplementation("com.github.tomakehurst:wiremock-jre8:$wireMockVersion") {
        exclude(group = "junit")
    }
    testImplementation("org.xmlunit:xmlunit-matchers:2.8.1")
}

configure<SourceSetContainer> {
    named("main") {
        java.srcDir("src/main/java")
        java.srcDir("$buildDir/generated-sources/xsd2java")
        java.srcDir("$buildDir/generated-sources/wsdl2java")
    }
}
