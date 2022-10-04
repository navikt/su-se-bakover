// Contains shared test-data, functions and extension funcions to be used across modules
dependencies {
    val kotestVersion = "5.5.0"

    implementation(project(":domain"))
    implementation(project(":common"))
    implementation(project(":client"))

    implementation("io.kotest:kotest-assertions-core:$kotestVersion")
    // TODO jah: Finn en måte å gjenbruke de versjonene her på.
    implementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
    implementation("org.skyscreamer:jsonassert:1.5.1")
}
