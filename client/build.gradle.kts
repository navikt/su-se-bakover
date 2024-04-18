val fuelVersion = "2.3.1"

dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))
    implementation(project(":oppgave:domain"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":tilbakekreving:infrastructure"))
    implementation(project(":person:domain"))
    implementation(project(":behandling:common:domain"))
    implementation(project(":behandling:klage:domain"))
    implementation(project(":satser"))
    implementation(project(":beregning"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":vilkår:skatt:domain"))
    implementation(project(":vilkår:skatt:infrastructure"))

    implementation(rootProject.libs.kittinunf.fuel)
    implementation(rootProject.libs.kittinunf.fuel.gson)

    implementation("org.json:json:20240303")

    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    // We exclude jdk15on because of security issues. We use jdk18on instead.
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")

    testImplementation(project(":test-common"))
    implementation(rootProject.libs.wiremock) {
        exclude(group = "junit")
    }
    implementation("org.jetbrains.kotlinx:atomicfu:0.24.0")
    testImplementation("org.xmlunit:xmlunit-matchers:2.9.1")
}

configurations {
    all {
        // Ref dependabot PR 8 - https://github.com/navikt/su-se-bakover/security/dependabot/1 https://github.com/advisories/GHSA-6xx3-rg99-gc3p https://github.com/navikt/su-se-bakover/security/dependabot/8 https://github.com/advisories/GHSA-hr8g-6v94-x4m9
        // We exclude this and include jdk18on instead.
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }
}
