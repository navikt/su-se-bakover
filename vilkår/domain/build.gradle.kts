dependencies {
    implementation(project(":common:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-domain")
}
