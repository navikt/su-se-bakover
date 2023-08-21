dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":service"))
    implementation(project(":client"))
    implementation(project(":hendelse:domain"))
    implementation(project(":økonomi:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("økonomi-infrastructure")
}
