
val markupVersion = "2.3.0"

dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":person:domain"))
    implementation("no.nav.brev.brevbaker:markup-model:$markupVersion")
    implementation("no.nav.brev.brevbaker:markup-dsl:$markupVersion")
    implementation(rootProject.libs.pdfbox)

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("dokument-domain")
}
