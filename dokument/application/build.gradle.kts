dependencies {
    implementation(project(":common:domain"))

    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))

    implementation(project(":hendelse:domain"))

    implementation(project(":person:domain"))

    implementation(project(":service"))
    implementation(project(":domain"))
    implementation(project(":client"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("dokument-application")
}
