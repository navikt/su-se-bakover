dependencies {
    implementation(project(":common:domain"))
    implementation(project(":dokument:domain"))
    implementation(project(":domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":person:domain"))
    implementation(project(":vilkår:utenlandsopphold:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-utenlandsopphold-application")
}
