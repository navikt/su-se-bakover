val fuelVersion = "2.3.1"
val wireMockVersion = "2.27.2"
val orgJsonVersion = "20201115"
val tjenestespesifikasjonVersion = "2558.a410ffd"
val cxfVersion = "3.4.2"
val jettyVersion = "9.4.36.v20210114"
dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))

    implementation("io.ktor:ktor-client-java:1.5.4")
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
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.12.1")

    implementation(enforcedPlatform("org.eclipse.jetty:jetty-bom:$jettyVersion")) {
        because("https://app.snyk.io/vuln/SNYK-JAVA-JUNIT-1017047")
    }
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
