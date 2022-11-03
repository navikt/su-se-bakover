dependencies {
    implementation(project(":utenlandsopphold:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":common"))
    implementation(project(":domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("utenlandsopphold-application")
}
