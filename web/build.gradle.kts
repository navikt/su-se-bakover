plugins {
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

avro {
    isGettersReturnOptional.set(true)
    isOptionalGettersForNullableFieldsOnly.set(true)
}

dependencies {
    implementation(project(":common:domain"))
    implementation(project(":common:infrastructure"))
    implementation(project(":common:presentation"))

    implementation(project(":domain"))
    implementation(project(":service"))
    implementation(project(":database"))
    implementation(project(":client"))
    implementation(project(":statistikk"))
    implementation(project(":hendelse:domain"))
    implementation(project(":hendelse:infrastructure"))
    implementation(project(":vilkår:utenlandsopphold:application"))
    implementation(project(":vilkår:utenlandsopphold:domain"))
    implementation(project(":vilkår:utenlandsopphold:infrastructure"))
    implementation(project(":kontrollsamtale:infrastructure"))
    implementation(project(":kontrollsamtale:application"))
    implementation(project(":kontrollsamtale:domain"))
    implementation(project(":vilkår:institusjonsopphold:infrastructure"))
    implementation(project(":vilkår:institusjonsopphold:application"))
    implementation(project(":vilkår:institusjonsopphold:domain"))
    implementation(project(":vilkår:institusjonsopphold:presentation"))
    implementation(project(":oppgave:infrastructure"))
    implementation(project(":oppgave:domain"))
    implementation(project(":økonomi:domain"))
    implementation(project(":økonomi:infrastructure"))

    implementation(project(":dokument:domain"))
    implementation(project(":dokument:infrastructure"))
    implementation(project(":dokument:application"))
    implementation(project(":dokument:presentation"))

    implementation(project(":person:domain"))

    implementation(project(":vedtak:domain"))

    implementation(project(":tilbakekreving:presentation"))
    implementation(project(":tilbakekreving:application"))
    implementation(project(":tilbakekreving:domain"))
    implementation(project(":tilbakekreving:infrastructure"))
    implementation(project(":hendelse:domain"))
    implementation(project(":behandling:domain"))
    implementation(project(":grunnbeløp"))
    implementation(project(":satser"))
    implementation(project(":vilkår:domain"))
    implementation(project(":vilkår:formue:domain"))
    implementation(project(":vilkår:uføre:domain"))
    implementation(project(":beregning"))

    testImplementation(project(":test-common"))
    testImplementation("org.awaitility:awaitility:4.2.0")
}

// Pluginen burde sette opp dette selv, men den virker discontinued.
tasks.named("compileKotlin").get().dependsOn(":web:generateAvroJava")
tasks.named("compileTestKotlin").get().dependsOn(":web:generateTestAvroJava")
