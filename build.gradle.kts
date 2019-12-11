plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.41"
    application
}

repositories {
    jcenter()
}

dependencies {
    compile(platform("org.jetbrains.kotlin:kotlin-bom"))
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testCompile("org.jetbrains.kotlin:kotlin-test")
    testCompile("org.jetbrains.kotlin:kotlin-test-junit")
}

application {
    mainClassName = "no.nav.su.AppKt"
}
