dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":hendelse:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("hendelse-infrastructure")
}
