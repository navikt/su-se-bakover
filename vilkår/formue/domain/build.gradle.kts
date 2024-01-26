dependencies {
    implementation(project(":common:domain"))
    implementation(project(":grunnbeløp"))
    implementation(project(":vilkår:common"))

    testImplementation(project(":test-common"))
    // TODO jah: pga. FormuegrenserFactoryTest.kt refererer til SatsFactoryForSupplerendeStønad (skal flyttes)
    testImplementation(project(":domain"))
    testImplementation(project(":satser"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-formue-domain")
}

tasks.test {
    useJUnitPlatform()
}
