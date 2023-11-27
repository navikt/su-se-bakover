dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":person:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("utenlandsopphold-domain")
}
