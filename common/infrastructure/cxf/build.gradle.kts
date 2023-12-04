dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation(rootProject.libs.cxf.rt.features.logging)
    implementation(rootProject.libs.cxf.rt.frontend.jaxws)
    implementation(rootProject.libs.cxf.rt.transports.http)
    implementation(rootProject.libs.cxf.rt.ws.security) {
        // https://security.snyk.io/vuln/SNYK-JAVA-ORGAPACHEVELOCITY-3116414
        exclude(group = "org.apache.velocity")
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("common-infrastructure-cxf")
}
