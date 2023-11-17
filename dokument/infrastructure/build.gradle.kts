dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation(project(":dokument:domain"))
    implementation(project(":dokument:application"))

    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":person:domain"))

    implementation(rootProject.libs.kittinunf.fuel)
    implementation(rootProject.libs.kittinunf.fuel.gson)

    testImplementation(project(":test-common"))
    testImplementation(rootProject.libs.wiremock)
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("dokument-infrastructure")
}
