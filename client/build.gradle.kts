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

    testImplementation(project(":test-common"))
    implementation(rootProject.libs.wiremock) {
        exclude(group = "junit")
    }
    implementation(rootProject.libs.kotlinx.atomicfu)
    testImplementation(rootProject.libs.xmlunit.matchers)
}
