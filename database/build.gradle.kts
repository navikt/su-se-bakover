dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation(project(":domain"))

    implementation(project(":vedtak:domain"))

    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))

    implementation(project(":økonomi:domain"))

    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))

    implementation(project(":vilkår:utenlandsopphold:domain"))
    implementation(project(":vilkår:utenlandsopphold:infrastructure"))
    implementation(project(":vilkår:institusjonsopphold:infrastructure"))
    implementation(project(":vilkår:institusjonsopphold:domain"))
    implementation(project(":vilkår:common"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":vilkår:flyktning:domain"))
    implementation(project(":vilkår:lovligopphold:domain"))
    implementation(project(":vilkår:fastopphold:domain"))
    implementation(project(":vilkår:pensjon:domain"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:opplysningsplikt:domain"))
    implementation(project(":vilkår:bosituasjon"))

    implementation(project(":oppgave:infrastructure"))
    implementation(project(":oppgave:domain"))

    implementation(project(":person:domain"))

    implementation(project(":tilbakekreving:domain"))
    implementation(project(":tilbakekreving:infrastructure"))

    implementation(project(":behandling:domain"))

    implementation(project(":grunnbeløp"))
    implementation(project(":satser"))
    implementation(project(":beregning"))

    testImplementation(project(":test-common"))
}
