dependencies {
    implementation(project(":common:domain"))
    implementation(project(":satser"))
    implementation(project(":beregning"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("behandling-domain")
}
