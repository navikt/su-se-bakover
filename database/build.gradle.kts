dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation("org.flywaydb:flyway-core:9.1.3")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.github.navikt:vault-jdbc:1.3.9")
    implementation("com.github.seratch:kotliquery:1.8.0")

    testImplementation(project(":test-common"))
}

plugins {
    id("com.github.hauner.jarTest") version "1.0.1" // bygger jar fil av testklassen slik at vi får tak i den fra de andre prosjektene i test
}
