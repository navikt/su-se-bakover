dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("common-presentation")
}
