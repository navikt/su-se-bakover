dependencies {
    implementation(project(":common:domain"))
    implementation(project(":grunnbeløp"))

    testImplementation(project(":test-common"))
    // TODO jah: pga. SatsFactoryForSupplerendeStønadUføreTest.kt refererer til SatsFactoryForSupplerendeStønad (skal flyttes)
    testImplementation(project(":domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("satser")
}
