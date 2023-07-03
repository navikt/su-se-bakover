dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))
    implementation(project(":institusjonsopphold:application"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("institusjonsopphold-presentation")
}
