dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("utenlandsopphold-domain")
}
