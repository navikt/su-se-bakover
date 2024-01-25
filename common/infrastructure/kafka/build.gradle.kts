dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))

    implementation("org.apache.kafka:kafka-clients:3.6.1") {
        exclude("org.apache.kafka", "kafka-raft")
        exclude("org.apache.kafka", "kafka-server-common")
        exclude("org.apache.kafka", "kafka-storage")
        exclude("org.apache.kafka", "kafka-storage-api")
        exclude("org.apache.kafka", "kafka-streams")
    }
    implementation("org.apache.avro:avro:1.11.3")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("common-infrastructure-avro")
}
