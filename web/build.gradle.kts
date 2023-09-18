plugins {
    id("com.github.davidmc24.gradle.plugin.avro") version "1.8.0"
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
    implementation(project(":institusjonsopphold:infrastructure"))
    implementation(project(":institusjonsopphold:application"))
    implementation(project(":institusjonsopphold:domain"))
    implementation(project(":institusjonsopphold:presentation"))
    implementation(project(":oppgave:infrastructure"))
    implementation(project(":oppgave:domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":økonomi:infrastructure"))
    implementation(project(":dokument:domain"))

    implementation(project(":tilbakekreving:presentation"))
    implementation(project(":tilbakekreving:application"))
    implementation(project(":tilbakekreving:domain"))

    testImplementation(project(":test-common"))
    testImplementation("org.awaitility:awaitility:4.2.0")
}

// Pluginen burde sette opp dette selv, men den virker discontinued.
tasks.named("compileKotlin").get().dependsOn(":web:generateAvroJava")
tasks.named("compileTestKotlin").get().dependsOn(":web:generateTestAvroJava")
