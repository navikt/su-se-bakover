dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":service"))
    implementation(project(":client"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":økonomi:domain"))
    implementation(project(":økonomi:application"))
    implementation(project(":oppgave:domain"))
    implementation(project(":vedtak:application"))
    implementation(project(":vilkår:uføre:domain"))

    testImplementation(project(":test-common"))
    testImplementation(project(":beregning"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("økonomi-infrastructure")
}
