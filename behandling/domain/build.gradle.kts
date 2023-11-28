dependencies {
    implementation(project(":common:domain"))
    implementation(project(":satser"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("behandling-domain")
}
