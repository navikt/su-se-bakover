dependencies {
    implementation(project(":common:domain"))
    implementation(project(":person:domain"))
    implementation(project(":person:application"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("person-infrastructure")
}
