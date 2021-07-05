val flywayVersion = "7.11.0"
val hikariVersion = "4.0.3"
val vaultJdbcVersion = "1.3.7"
val kotliqueryVersion = "1.3.1"
dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation(project(":test-common"))
    testImplementation("com.opentable.components:otj-pg-embedded:0.13.4") {
        exclude(group = "com.github.spotbugs")
    }
}

plugins {
    id("com.github.hauner.jarTest") version "1.0.1" // bygger jar fil av testklassen slik at vi får tak i den fra de andre prosjektene i test
}
