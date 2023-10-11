dependencies {
    implementation(project(":common:domain"))
    implementation(project(":oppgave:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("oppgave-application")
}

tasks.test {
    useJUnitPlatform()
}
