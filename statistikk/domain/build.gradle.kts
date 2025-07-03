dependencies {
    implementation(project(":vilkår:bosituasjon:domain"))
    implementation(project(":common:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("statistikk-domain")
}
