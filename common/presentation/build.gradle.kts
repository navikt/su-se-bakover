dependencies {
    implementation(project(":beregning"))
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":vilkår:inntekt:domain"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":vilkår:common"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("common-presentation")
}
