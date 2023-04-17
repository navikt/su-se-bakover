// Contains shared test-data, functions and extension funcions to be used across modules
dependencies {
    val kotestVersion = "5.6.0"

    implementation(project(":domain"))
    implementation(project(":common"))
    implementation(project(":client"))
    implementation(project(":database"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":utenlandsopphold:domain"))
    implementation(project(":kontrollsamtale:domain"))

    compileOnly("io.kotest:kotest-assertions-core:$kotestVersion")
    // TODO jah: Finn en måte å gjenbruke de versjonene her på.
    compileOnly("org.mockito.kotlin:mockito-kotlin:4.1.0")
    compileOnly("org.skyscreamer:jsonassert:1.5.1")
    compileOnly("io.zonky.test:embedded-postgres:2.0.3")
}
