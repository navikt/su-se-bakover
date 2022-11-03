dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":utenlandsopphold:domain"))
    implementation(project(":utenlandsopphold:application"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("utenlandsopphold-infrastructure")
}
