

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        val kotlinVersion: String by settings
        kotlin("jvm") version kotlinVersion
    }
}
/*dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("slf4j", "2.0.9")
            library("slf4j-api", "org.slf4j", "slf4j-api").versionRef("slf4j")
            library("jul-to-slf4j", "org.slf4j", "jul-to-slf4j").versionRef("slf4j")
            library("jcl-over-slf4j", "org.slf4j", "jcl-over-slf4j").versionRef("slf4j")
            library("log4j-over-slf4j", "org.slf4j", "log4j-over-slf4j").versionRef("slf4j")
        }
    }
}*/



rootProject.name = "su-se-bakover"
include("domain")
include("application")
include("web")
include("common:domain")
include("common:infrastructure")
include("service")
include("database")
include("client")
include("test-common")
include("web-regresjonstest")
include("statistikk")

include("hendelse:infrastructure")
include("hendelse:domain")

include("utenlandsopphold:infrastructure")
include("utenlandsopphold:application")
include("utenlandsopphold:domain")

include("datapakker:soknad")

include("kontrollsamtale:infrastructure")
include("kontrollsamtale:application")
include("kontrollsamtale:domain")
include("institusjonsopphold:infrastructure")
include("institusjonsopphold:application")
include("institusjonsopphold:domain")
include("institusjonsopphold:presentation")
include("oppgave:domain")
include("oppgave:infrastructure")

include("økonomi:domain")
include("økonomi:infrastructure")
include("økonomi:application")

include("tilbakekreving:presentation")
include("tilbakekreving:application")
include("tilbakekreving:domain")
include("tilbakekreving:infrastructure")

include("person:domain")
include("person:infrastructure")
include("person:application")

include("dokument:domain")
include("dokument:database")
include("dokument:application")
include("dokument:infrastructure")
