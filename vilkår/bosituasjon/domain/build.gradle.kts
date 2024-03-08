dependencies {
    implementation(project(":common:domain"))
    implementation(project(":vilkår:common"))
    implementation(project(":satser"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-bosituasjon-domain")
}

tasks.test {
    useJUnitPlatform()
}
