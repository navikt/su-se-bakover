// Søknadsinnhold er skilt ut fra :domain slik at :dokument:domain kan bruke det uten sirkulær avhengighet.
dependencies {
    implementation(project(":common:domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("søknad-domain")
}
