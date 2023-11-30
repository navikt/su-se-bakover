dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":person:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":beregning"))
    implementation(project(":vilkår:domain"))
    implementation(project(":vilkår:uføre:domain"))

    testImplementation(project(":økonomi:domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("statistikk")
}
