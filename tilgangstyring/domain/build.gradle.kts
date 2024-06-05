dependencies {
    implementation(project(":common:domain"))
    implementation(project(":person:domain"))
    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("tilgangstyring-domain")
}
