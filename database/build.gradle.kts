val flywayVersion = "6.2.1"
val hikariVersion = "3.3.1"
val vaultJdbcVersion = "1.3.1"
val kotliqueryVersion = "1.3.1"
dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("com.opentable.components:otj-pg-embedded:0.13.3")
}
