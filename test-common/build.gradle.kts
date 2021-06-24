// Contains shared test-data, functions and extension funcions to be used across modules
dependencies {
    val kotestVersion = "4.6.0"

    implementation(project(":domain"))
    implementation(project(":common"))

    implementation("io.kotest:kotest-assertions-core:$kotestVersion")

}
