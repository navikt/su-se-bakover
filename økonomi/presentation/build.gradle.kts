dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation(project(":økonomi:domain"))
    implementation(project(":økonomi:application"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("økonomi-presentation")
}

tasks.test {
    useJUnitPlatform()
}
