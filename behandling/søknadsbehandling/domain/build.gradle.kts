dependencies {
    implementation(project(":behandling:domain"))
    implementation(project(":beregning"))
    implementation(project(":common:domain"))
    implementation(project(":common:domain"))
    implementation(project(":oppgave:domain"))
    implementation(project(":person:domain"))
    implementation(project(":vilkår:bosituasjon:domain"))
    implementation(project(":vilkår:common"))
    implementation(project(":vilkår:familiegjenforening:domain"))
    implementation(project(":vilkår:fastopphold:domain"))
    implementation(project(":vilkår:flyktning:domain"))
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
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("behandling-søknadsbehandling-domain")
}
