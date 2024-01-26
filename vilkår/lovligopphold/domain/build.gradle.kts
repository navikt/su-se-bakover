dependencies {
    implementation(project(":common:domain"))
    implementation(project(":vilkår:common"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-lovligopphold-domain")
}

tasks.test {
    useJUnitPlatform()
}
