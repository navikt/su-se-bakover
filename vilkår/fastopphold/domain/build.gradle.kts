dependencies {
    implementation(project(":common:domain"))
    implementation(project(":vilkår:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-fastopphold-domain")
}

tasks.test {
    useJUnitPlatform()
}
