dependencies {
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-skatt-domain")
}

tasks.test {
    useJUnitPlatform()
}
