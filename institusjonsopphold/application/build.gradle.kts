dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":institusjonsopphold:domain"))
    implementation(project(":institusjonsopphold:infrastructure"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("institusjonsopphold-application")
}

tasks.test {
    useJUnitPlatform()
}
