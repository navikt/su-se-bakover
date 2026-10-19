dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":person:domain"))
    implementation(rootProject.libs.pdfbox)

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("dokument-domain")
}
