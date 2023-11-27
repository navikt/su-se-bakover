// Contains shared test-data, functions and extension funcions to be used across modules
dependencies {
    val kotestVersion = "5.8.0"

    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":client"))
    implementation(project(":database"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":utenlandsopphold:domain"))
    implementation(project(":kontrollsamtale:domain"))
    implementation(project(":institusjonsopphold:domain"))
    implementation(project(":institusjonsopphold:presentation"))
    implementation(project(":oppgave:domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":tilbakekreving:presentation"))
    implementation(project(":tilbakekreving:infrastructure"))
    implementation(project(":person:domain"))

    compileOnly("io.kotest:kotest-assertions-core:$kotestVersion")
    // TODO jah: Finn en måte å gjenbruke de versjonene her på.
    compileOnly("org.mockito.kotlin:mockito-kotlin:5.1.0")
    compileOnly("org.skyscreamer:jsonassert:1.5.1")
    compileOnly("io.zonky.test:embedded-postgres:2.0.5")
    compileOnly(rootProject.libs.jupiter.api)
    implementation(rootProject.libs.wiremock) {
        exclude(group = "junit")
    }
}
