dependencies {
    implementation(project(":common:domain"))
    implementation(project(":vilkår:common"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":vilkår:pensjon:domain"))
    implementation(project(":vilkår:flyktning:domain"))
    implementation(project(":vilkår:fastopphold:domain"))
    implementation(project(":vilkår:lovligopphold:domain"))
    implementation(project(":vilkår:institusjonsopphold:domain"))
    implementation(project(":vilkår:utenlandsopphold:domain"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:bosituasjon"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-vurderinger-domain")
}
