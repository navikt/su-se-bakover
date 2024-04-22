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

    implementation(rootProject.libs.org.json)

    implementation(rootProject.libs.nimbus.jose.jwt)
    // We exclude jdk15on because of security issues. We use jdk18on instead.
    implementation(rootProject.libs.bcprov.jdk18on)

    testImplementation(project(":test-common"))
    implementation(rootProject.libs.wiremock) {
        exclude(group = "junit")
    }
    implementation(rootProject.libs.kotlinx.atomicfu)
    testImplementation(rootProject.libs.xmlunit.matchers)
}

configurations {
    all {
        // Ref dependabot PR 8 - https://github.com/navikt/su-se-bakover/security/dependabot/1 https://github.com/advisories/GHSA-6xx3-rg99-gc3p https://github.com/navikt/su-se-bakover/security/dependabot/8 https://github.com/advisories/GHSA-hr8g-6v94-x4m9
        // We exclude this and include jdk18on instead.
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }
}
