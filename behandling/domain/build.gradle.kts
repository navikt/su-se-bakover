dependencies {
    implementation(project(":common:domain"))
    implementation(project(":sats"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("behandling-domain")
}
