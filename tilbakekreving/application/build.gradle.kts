dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))

    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))

    implementation(project(":tilbakekreving:domain"))
    implementation(project(":tilbakekreving:infrastructure"))
    implementation(project(":person:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("tilbakekreving-application")
}

tasks.test {
    useJUnitPlatform()
}
