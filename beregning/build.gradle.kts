dependencies {
    implementation(project(":common:domain"))
    implementation(project(":satser"))
    implementation(project(":grunnbeløp"))
    implementation(project(":vilkår:common"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:bosituasjon:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("beregning")
}
