pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "su-se-bakover"

include("application")
include("behandling:domain")
include("behandling:klage:domain")
include("behandling:regulering:domain")
include("behandling:revurdering:domain")
include("behandling:søknadsbehandling:domain")
include("beregning")
include("client")
include("common:domain")
include("common:infrastructure")
include("common:infrastructure:cxf")
include("common:presentation")
include("database")
include("datapakker:soknad")
include("dokument:application")
include("dokument:domain")
include("dokument:infrastructure")
include("dokument:presentation")
include("domain")
include("grunnbeløp")
include("hendelse:domain")
include("hendelse:infrastructure")
include("kontrollsamtale:application")
include("kontrollsamtale:domain")
include("kontrollsamtale:infrastructure")
include("oppgave:application")
include("oppgave:domain")
include("oppgave:infrastructure")
include("person:application")
include("person:domain")
include("person:infrastructure")
include("satser")
include("service")
include("statistikk")
include("test-common")
include("tilbakekreving:application")
include("tilbakekreving:domain")
include("tilbakekreving:infrastructure")
include("tilbakekreving:presentation")
include("vedtak:application")
include("vedtak:domain")
include("web")
include("web-regresjonstest")
include("økonomi:application")
include("økonomi:domain")
include("økonomi:infrastructure")
include("økonomi:presentation")

//---- Vilkår ------
include("vilkår:common")
include("vilkår:vurderinger")
include("vilkår:uføre:domain")
include("vilkår:flyktning:domain")
include("vilkår:pensjon:domain")
include("vilkår:fastopphold:domain")
include("vilkår:lovligopphold:domain")
include("vilkår:institusjonsopphold:application")
include("vilkår:institusjonsopphold:domain")
include("vilkår:institusjonsopphold:infrastructure")
include("vilkår:institusjonsopphold:presentation")
include("vilkår:utenlandsopphold:application")
include("vilkår:utenlandsopphold:domain")
include("vilkår:utenlandsopphold:infrastructure")
include("vilkår:formue:domain")
include("vilkår:inntekt:domain")
include("vilkår:bosituasjon")
include("vilkår:opplysningsplikt")
include("vilkår:opplysningsplikt:domain")
include("vilkår:personligoppmøte")
include("vilkår:personligoppmøte:domain")
include("vilkår:familiegjenforening")
include("vilkår:familiegjenforening:domain")
