plugins {
    id("com.github.davidmc24.gradle.plugin.avro") version "1.7.1"
}

avro {
    isGettersReturnOptional.set(true)
    isOptionalGettersForNullableFieldsOnly.set(true)
}

dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":domain"))
    implementation(project(":service"))
    implementation(project(":database"))
    implementation(project(":client"))
    implementation(project(":statistikk"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":utenlandsopphold:application"))
    implementation(project(":utenlandsopphold:domain"))
    implementation(project(":utenlandsopphold:infrastructure"))
    implementation(project(":kontrollsamtale:infrastructure"))
    implementation(project(":kontrollsamtale:application"))
    implementation(project(":kontrollsamtale:domain"))

    testImplementation(project(":test-common"))
    testImplementation("org.awaitility:awaitility:4.2.0")
}

// Pluginen burde sette opp dette selv, men den virker discontinued.
tasks.named("compileKotlin").get().dependsOn(":web:generateAvroJava")
tasks.named("compileTestKotlin").get().dependsOn(":web:generateTestAvroJava")
