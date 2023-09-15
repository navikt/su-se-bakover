dependencies {
    implementation(project(":tilbakekreving:application"))
    implementation(project(":tilbakekreving:domain"))

    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation(project(":domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("tilbakekreving-presentation")
}

tasks.test {
    useJUnitPlatform()
}
