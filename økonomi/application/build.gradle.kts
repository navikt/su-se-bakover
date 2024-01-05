dependencies {
    implementation(project(":common:domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":service"))
    implementation(project(":domain"))
    implementation(project(":oppgave:domain"))
    implementation(project(":vedtak:domain"))
    implementation(project(":beregning"))
    implementation(project(":satser"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("økonomi-application")
}
