dependencies {
    implementation(project(":common:domain"))
    implementation(project(":vilkår:common"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-inntekt-domain")
}

tasks.test {
    useJUnitPlatform()
}
