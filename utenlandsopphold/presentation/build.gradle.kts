dependencies {
    implementation(project(":utenlandsopphold:application"))
    implementation(project(":utenlandsopphold:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("utenlandsopphold-presentation")
}
