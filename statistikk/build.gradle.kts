dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":vedtak:domain"))
    implementation(project(":person:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":beregning"))
    implementation(project(":vilkår:common"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":vilkår:bosituasjon"))

    testImplementation(project(":økonomi:domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("statistikk")
}
