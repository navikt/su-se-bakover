dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))
    // avhengig av økonomi pga sak
    implementation(project(":økonomi:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":oppgave:domain"))
    implementation(project(":institusjonsopphold:domain"))
    implementation(project(":institusjonsopphold:infrastructure"))
    implementation(project(":person:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("institusjonsopphold-application")
}

tasks.test {
    useJUnitPlatform()
}
