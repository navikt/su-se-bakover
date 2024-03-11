dependencies {
    implementation(project(":common:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":domain"))
    implementation(project(":vedtak:domain"))
    implementation(project(":vedtak:application"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":satser"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("behandling-regulering-application")
}
