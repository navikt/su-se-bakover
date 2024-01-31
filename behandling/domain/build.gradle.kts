dependencies {
    implementation(project(":common:domain"))
    implementation(project(":satser"))
    implementation(project(":beregning"))

    implementation(project(":vilkår:vurderinger"))
    implementation(project(":økonomi:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("behandling-domain")
}
