val orgJsonVersion = "20180813"

dependencies {
    implementation(project(":common"))
    implementation("no.nav:su-meldinger:7e1f3d035dd830764448cdd7b288110dda211d61")

    implementation("org.json:json:$orgJsonVersion")
}