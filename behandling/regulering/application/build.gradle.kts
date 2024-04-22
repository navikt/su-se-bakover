dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))
    implementation(project(":vedtak:domain"))
    implementation(project(":vedtak:application"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":satser"))
    implementation(project(":økonomi:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("behandling-regulering-application")
}
