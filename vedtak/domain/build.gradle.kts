dependencies {
    implementation(project(":behandling:common:domain"))
    implementation(project(":behandling:revurdering:domain"))
    implementation(project(":behandling:søknadsbehandling:domain"))
    implementation(project(":beregning"))
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":person:domain"))
    implementation(project(":vilkår:bosituasjon:domain"))
    implementation(project(":vilkår:common"))
    implementation(project(":vilkår:familiegjenforening:domain"))
    implementation(project(":vilkår:fastopphold:domain"))
    implementation(project(":vilkår:flyktning:domain"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:institusjonsopphold:domain"))
    implementation(project(":vilkår:lovligopphold:domain"))
    implementation(project(":vilkår:opplysningsplikt:domain"))
    implementation(project(":vilkår:pensjon:domain"))
    implementation(project(":vilkår:personligoppmøte:domain"))
    implementation(project(":vilkår:skatt:domain"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":vilkår:utenlandsopphold:domain"))
    implementation(project(":vilkår:vurderinger:domain"))
    implementation(project(":økonomi:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vedtak-domain")
}

tasks.test {
    useJUnitPlatform()
}
