val confluentVersion = "7.3.1"
val kafkaVersion = "3.3.2"

dependencies {
    testImplementation(project(":common"))
    testImplementation(project(":domain"))
    testImplementation(project(":test-common"))
    testImplementation(project(":service"))
    testImplementation(project(":web"))

    testImplementation("no.nav:kafka-embedded-env:3.2.1") {
        // Breaks build: exclude(group = "org.glassfish.jersey.ext", module = "jersey-bean-validation")
        // Breaks build: exclude(group = "org.glassfish", module = "jakarta.el")
        // Breaks build: exclude(group = "org.eclipse.jetty", module = "jetty-server")
        // Breaks build: exclude(group = "org.eclipse.jetty", module = "jetty-servlets")
        exclude("org.eclipse.jetty", module = "jetty-webapp")
        exclude("log4j") // module = "log4j"
        exclude("io.netty") // module = "netty-handler"
        exclude("io.grpc") // module = "grpc-core"
        exclude("io.swagger.core.v3") // module = "swagger-core"
        exclude("org.jetbrains.kotlin")
        exclude("org.jetbrains.kotlinx")
        exclude("io.confluent", "kafka-protobuf-types")
        exclude("io.confluent", "kafka-schema-registry-client")
        exclude("io.confluent", "kafka-schema-serializer")
        exclude("io.confluent", "kafka-avro-serializer")
        exclude("io.confluent", "common-utils")
        exclude("org.apache.kafka", "kafka-clients")
        exclude("org.apache.kafka", "kafka-storage-api")
        exclude("com.google.protobuf")
    }

    constraints {
        implementation("io.confluent:kafka-schema-registry") {
            version {
                strictly("$confluentVersion")
            }
        }
        implementation("io:confluent:kafka-json-schema-provider") {
            version {
                strictly("$confluentVersion")
            }
        }
        implementation("io.confluent:kafka-protobuf-provider") {
            version {
                strictly("$confluentVersion")
            }
        }
        implementation("org.apache.kafka:kafka-storage") {
            version {
                strictly("$kafkaVersion")
            }
        }
        implementation("org.apache.kafka:kafka-streams") {
            version {
                strictly("$kafkaVersion")
            }
        }
        implementation("org.apache.kafka:kafka-raft") {
            version {
                strictly("$kafkaVersion")
            }
        }
        implementation("org.apache.kafka:kafka-server-common") {
            version {
                strictly("$kafkaVersion")
            }
        }
        implementation("org.apache.kafka:kafka_2.13") {
            version {
                strictly("$kafkaVersion")
            }
        }
        implementation("org.apache.kafka:metadata") {
            version {
                strictly("$kafkaVersion")
            }
        }
    }
}
