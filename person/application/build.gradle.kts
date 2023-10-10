dependencies {
    implementation(project(":common:domain"))
    implementation(project(":person:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("person-application")
}
