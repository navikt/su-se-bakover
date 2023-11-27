dependencies {
    implementation(project(":common:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-formue-domain")
}

tasks.test {
    useJUnitPlatform()
}
