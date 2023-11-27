dependencies {
    implementation(project(":domain"))
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":vilkår:institusjonsopphold:domain"))
    // avhengig av økonomi pga sak
    implementation(project(":økonomi:domain"))

    implementation(project(":common:infrastructure"))
    implementation(project(":hendelse:infrastructure"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("vilkår-institusjonsopphold-infrastructure")
}

tasks.test {
    useJUnitPlatform()
}
