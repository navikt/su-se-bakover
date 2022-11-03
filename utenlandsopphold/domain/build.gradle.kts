dependencies {
    implementation(project(":common"))
    implementation(project(":hendelse:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("utenlandsopphold-domain")
}
