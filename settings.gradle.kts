pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
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
include("oppgave:application")
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
include("dokument:application")
include("dokument:infrastructure")
include("dokument:presentation")
include("common:presentation")
include("behandling:domain")
include("behandling:søknadsbehandling:domain")
include("behandling:revurdering:domain")
include("behandling:regulering:domain")
include("behandling:klage:domain")
