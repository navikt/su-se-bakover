dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))

    implementation(project(":vedtak:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vedtak-application")
}

tasks.test {
    useJUnitPlatform()
}