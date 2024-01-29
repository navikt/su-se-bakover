dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":oppgave:domain"))
    implementation(project(":vilkår:common"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-institusjonsopphold-domain")
}

tasks.test {
    useJUnitPlatform()
}
