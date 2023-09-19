dependencies {
    implementation(project(":utenlandsopphold:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":common:domain"))
    implementation(project(":domain"))
    implementation(project(":person:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("utenlandsopphold-application")
}
