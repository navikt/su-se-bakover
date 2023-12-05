dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":hendelse:infrastructure"))

    implementation(project(":oppgave:domain"))

    testImplementation(project(":domain"))
    testImplementation(project(":test-common"))
    testImplementation(project(":satser"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("oppgave-infrastructure")
}

tasks.test {
    useJUnitPlatform()
}
