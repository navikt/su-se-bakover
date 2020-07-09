val orgJsonVersion = "20180813"

dependencies {
    implementation(project(":common"))

    implementation("org.json:json:$orgJsonVersion")
}
plugins {
    id("com.github.hauner.jarTest") version "1.0.1" // bygger jar fil av testklassen slik at vi får tak i den fra de andre prosjektene i test
}
