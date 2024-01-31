dependencies {
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))
    implementation(project(":person:domain"))
    implementation(project(":vilkår:skatt:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-skatt-application")
}

tasks.test {
    useJUnitPlatform()
}
