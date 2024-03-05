dependencies {
    implementation(platform("com.google.cloud:libraries-bom:26.33.0"))
    implementation("com.google.cloud:google-cloud-bigquery")
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
}
