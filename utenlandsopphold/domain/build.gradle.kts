dependencies {
    implementation(project(":common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("utenlandsopphold-domain")
}
