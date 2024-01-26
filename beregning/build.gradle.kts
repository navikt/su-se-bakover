dependencies {
    implementation(project(":common:domain"))
    implementation(project(":satser"))
    implementation(project(":grunnbeløp"))
    implementation(project(":vilkår:inntekt:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("beregning")
}
