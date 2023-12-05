// Contains shared test-data, functions and extension funcions to be used across modules
dependencies {
    val kotestVersion = "5.8.0"

    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":service"))
    implementation(project(":client"))
    implementation(project(":database"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":vilkår:utenlandsopphold:domain"))
    implementation(project(":kontrollsamtale:domain"))
    implementation(project(":vilkår:institusjonsopphold:domain"))
    implementation(project(":vilkår:institusjonsopphold:presentation"))
    implementation(project(":oppgave:domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":tilbakekreving:presentation"))
    implementation(project(":tilbakekreving:infrastructure"))
    implementation(project(":tilbakekreving:application"))
    implementation(project(":person:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":grunnbeløp"))
    implementation(project(":satser"))
    implementation(project(":vilkår:domain"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":beregning"))
    implementation(project(":vedtak:domain"))

    compileOnly("io.kotest:kotest-assertions-core:$kotestVersion")
    // TODO jah: Kan gjenbruke versjoner ved å bruke gradle/libs.versions.toml
    compileOnly("org.mockito.kotlin:mockito-kotlin:5.2.1")
    compileOnly("org.skyscreamer:jsonassert:1.5.1")
    compileOnly("io.zonky.test:embedded-postgres:2.0.6")
    compileOnly(rootProject.libs.jupiter.api)
    implementation(rootProject.libs.wiremock) {
        exclude(group = "junit")
    }
}
