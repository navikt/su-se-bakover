dependencies {
    implementation(project(":institusjonsopphold:application"))
    implementation(project(":institusjonsopphold:domain"))

    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation(project(":domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("institusjonsopphold-presentation")
}
