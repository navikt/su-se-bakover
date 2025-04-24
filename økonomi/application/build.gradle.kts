dependencies {
    implementation(project(":behandling:common:domain"))
    implementation(project(":common:domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":domain"))
    implementation(project(":oppgave:domain"))
    implementation(project(":vedtak:domain"))
    implementation(project(":vedtak:application"))
    implementation(project(":beregning"))
    implementation(project(":satser"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("økonomi-application")
}
