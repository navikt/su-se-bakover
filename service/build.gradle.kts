dependencies {
    implementation(project(":behandling:behandlinger:domain"))
    implementation(project(":behandling:common:domain"))
    implementation(project(":behandling:klage:domain"))
    implementation(project(":behandling:regulering:domain"))
    implementation(project(":behandling:revurdering:domain"))
    implementation(project(":behandling:søknadsbehandling:domain"))
    implementation(project(":beregning"))
    implementation(project(":client"))
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":nøkkeltall:domain"))
    implementation(project(":oppgave:domain"))
    implementation(project(":person:domain"))
    implementation(project(":satser"))
    implementation(project(":statistikk"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":vedtak:application"))
    implementation(project(":vedtak:domain"))
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
    implementation(project(":vilkår:skatt:application"))
    implementation(project(":vilkår:skatt:domain"))
    implementation(project(":vilkår:skatt:infrastructure"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":vilkår:utenlandsopphold:domain"))
    implementation(project(":vilkår:vurderinger:domain"))
    implementation(project(":økonomi:domain"))

    testImplementation(project(":test-common"))
    testImplementation(project(":vilkår:utenlandsopphold:domain"))
}
