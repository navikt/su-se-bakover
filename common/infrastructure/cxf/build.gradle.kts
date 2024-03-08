dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation(rootProject.libs.cxf.rt.features.logging)
    implementation(rootProject.libs.cxf.rt.frontend.jaxws)
    implementation(rootProject.libs.cxf.rt.transports.http)
    implementation(rootProject.libs.cxf.rt.ws.security) {
        // https://security.snyk.io/vuln/SNYK-JAVA-ORGAPACHEVELOCITY-3116414
        exclude(group = "org.apache.velocity")
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }
    // We exclude jdk15on because of security issues. We use jdk18on instead.
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("common-infrastructure-cxf")
}

configurations {
    all {
        // Ref dependabot PR 8 - https://github.com/navikt/su-se-bakover/security/dependabot/1 https://github.com/advisories/GHSA-6xx3-rg99-gc3p https://github.com/navikt/su-se-bakover/security/dependabot/8 https://github.com/advisories/GHSA-hr8g-6v94-x4m9
        // We exclude this and include jdk18on instead.
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }
}
