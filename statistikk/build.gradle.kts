dependencies {
    implementation(project(":behandling:common:domain"))
    implementation(project(":behandling:klage:domain"))
    implementation(project(":behandling:regulering:domain"))
    implementation(project(":behandling:revurdering:domain"))
    implementation(project(":behandling:søknadsbehandling:domain"))
    implementation(project(":beregning"))
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":person:domain"))
    implementation(project(":vedtak:domain"))
    implementation(project(":vilkår:bosituasjon:domain"))
    implementation(project(":vilkår:common"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":vilkår:vurderinger:domain"))
    implementation(project(":vilkår:familiegjenforening:domain"))

    testImplementation(project(":økonomi:domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("statistikk")
}
