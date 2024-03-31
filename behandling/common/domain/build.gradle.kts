dependencies {
    implementation(project(":beregning"))
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":satser"))
    implementation(project(":vilkår:bosituasjon:domain"))
    implementation(project(":vilkår:common"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:opplysningsplikt:domain"))
    implementation(project(":vilkår:skatt:domain"))
    implementation(project(":vilkår:vurderinger:domain"))
    implementation(project(":økonomi:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("behandling-common-domain")
}
