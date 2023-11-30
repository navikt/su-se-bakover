dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":person:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":beregning"))

    testImplementation(project(":økonomi:domain"))
    testImplementation(project(":test-common"))
    testImplementation(project(":vilkår:uføre:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("statistikk")
}
