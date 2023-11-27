dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":oppgave:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-institusjonsopphold-domain")
}

tasks.test {
    useJUnitPlatform()
}
