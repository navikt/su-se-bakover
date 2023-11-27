dependencies {
    implementation(project(":vilkår:institusjonsopphold:application"))
    implementation(project(":vilkår:institusjonsopphold:domain"))
    implementation(project(":hendelse:domain"))

    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation(project(":domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-institusjonsopphold-presentation")
}

tasks.test {
    useJUnitPlatform()
}
