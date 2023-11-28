dependencies {
    implementation(project(":common:domain"))

    testImplementation(project(":test-common"))
    testImplementation(project(":satser"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("grunnbeløp")
}
