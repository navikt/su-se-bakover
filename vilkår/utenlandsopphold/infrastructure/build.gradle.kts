dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":vilkår:utenlandsopphold:domain"))
    implementation(project(":vilkår:utenlandsopphold:application"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-utenlandsopphold-infrastructure")
}
