dependencies {
    implementation(project(":common:domain"))
    implementation(project(":sats"))

    testImplementation(project(":test-common"))
    // TODO jah: pga. FormuegrenserFactoryTest.kt refererer til SatsFactoryForSupplerendeStønad (skal flyttes)
    testImplementation(project(":domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-formue-domain")
}

tasks.test {
    useJUnitPlatform()
}
