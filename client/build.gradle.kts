val tjenestespesifikasjonVersion = "2618.0448179"
val cxfVersion = "3.5.5"

dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))
    implementation(project(":oppgave:domain"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":person:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":satser"))
    implementation(project(":beregning"))
    implementation(project(":vilkår:uføre:domain"))

    implementation(rootProject.libs.kittinunf.fuel)
    implementation(rootProject.libs.kittinunf.fuel.gson)
    implementation("javax.xml.bind:jaxb-api:2.3.1")

    implementation("org.json:json:20231013")

    implementation(
        "com.github.navikt.tjenestespesifikasjoner:nav-virksomhet-oppdragsbehandling-v1-meldingsdefinisjon:$tjenestespesifikasjonVersion",
    )
    implementation(
        "com.github.navikt.tjenestespesifikasjoner:nav-system-os-simuler-fp-service-tjenestespesifikasjon:$tjenestespesifikasjonVersion",
    )
    implementation("com.github.navikt.tjenestespesifikasjoner:tilbakekreving-v1-tjenestespesifikasjon:$tjenestespesifikasjonVersion")
    implementation("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-ws-security:$cxfVersion") {
        // https://security.snyk.io/vuln/SNYK-JAVA-ORGAPACHEVELOCITY-3116414
        exclude(group = "org.apache.velocity")
    }
    implementation("javax.xml.ws:jaxws-api:2.3.1")
    implementation("javax.jws:javax.jws-api:1.1")
    // Fails to find SAAJMetaFactoryImpl when either missing or if you upgrade to 2.0.0
    implementation("com.sun.xml.messaging.saaj:saaj-impl:1.5.2")
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.1")

    testImplementation(project(":test-common"))
    implementation(rootProject.libs.wiremock) {
        exclude(group = "junit")
    }
    testImplementation("org.xmlunit:xmlunit-matchers:2.9.1")
}

configure<SourceSetContainer> {
    named("main") {
        java.srcDir("src/main/java")

        // For xsd2java
        val xsd2javaDir: Provider<Directory> = layout.buildDirectory.dir("generated-sources/xsd2java")
        java.srcDir(xsd2javaDir.map { it.asFile })

        // For wsdl2java
        val wsdl2javaDir: Provider<Directory> = layout.buildDirectory.dir("generated-sources/wsdl2java")
        java.srcDir(wsdl2javaDir.map { it.asFile })
    }
}
