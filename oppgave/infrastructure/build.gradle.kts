dependencies {
    implementation(project(":domain"))
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":oppgave:domain"))

    implementation(project(":hendelse:infrastructure"))
    implementation(project(":common:infrastructure"))
    implementation(project(":hendelse:infrastructure"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("institusjonsopphold-infrastructure")
}

tasks.test {
    useJUnitPlatform()
}
