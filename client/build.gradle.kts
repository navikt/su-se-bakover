val fuelVersion = "2.2.1"
val wireMockVersion = "2.23.2"
val orgJsonVersion = "20180813"

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))

    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("net.logstash.logback:logstash-logback-encoder:5.2")
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-gson:$fuelVersion")
    implementation("org.apache.kafka:kafka-streams:2.3.0")

    implementation("org.json:json:$orgJsonVersion")

    testImplementation("com.github.tomakehurst:wiremock:$wireMockVersion") {
        exclude(group = "junit")
    }
}