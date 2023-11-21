dependencies {
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":person:domain"))

    implementation(project(":domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("dokument-application")
}
