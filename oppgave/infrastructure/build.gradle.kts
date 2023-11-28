dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":oppgave:domain"))

    implementation(project(":hendelse:infrastructure"))
    implementation(project(":common:infrastructure"))
    implementation(project(":hendelse:infrastructure"))

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
