dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":dokument:domain"))

    testImplementation(project(":test-common"))
    testImplementation(project(":beregning"))
    // TODO jah: Try to remove this dependency
    testImplementation(project(":domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("økonomi-domain")
}
