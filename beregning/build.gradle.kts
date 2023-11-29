dependencies {
    implementation(project(":common:domain"))
    implementation(project(":satser"))
    implementation(project(":grunnbeløp"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("beregning")
}
