dependencies {
    implementation(project(":common:domain"))
    implementation(project(":domain"))

    implementation(project(":hendelse:domain"))

    implementation(project(":tilbakekreving:domain"))
    implementation(project(":person:domain"))
    implementation(project(":service"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("tilbakekreving-application")
}

tasks.test {
    useJUnitPlatform()
}
