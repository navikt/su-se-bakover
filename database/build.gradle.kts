val flywayVersion = "6.2.1"
val hikariVersion = "3.3.1"
val vaultJdbcVersion = "1.3.1"
val kotliqueryVersion = "1.3.1"
val orgJsonVersion = "20180813"
dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    implementation("org.json:json:$orgJsonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.11.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.11.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.1")

    testImplementation("com.opentable.components:otj-pg-embedded:0.13.3")
}

plugins {
    id("com.github.hauner.jarTest") version "1.0.1" // bygger jar fil av testklassen slik at vi får tak i den fra de andre prosjektene i test
}
