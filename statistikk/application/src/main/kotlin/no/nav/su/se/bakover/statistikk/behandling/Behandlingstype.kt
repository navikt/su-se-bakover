package no.nav.su.se.bakover.statistikk.behandling

internal enum class Behandlingstype(val beskrivelse: String) {
    SOKNAD("Søknad for SU Uføre"),
    REVURDERING("Revurdering av søknad for SU Uføre"),
    KLAGE("Klage for SU Uføre"),
    OMGJØRING_AVSLAG("Omgjøring av avslått søknad"),
    OMGJØRING("Omgjøring av behandling"),
}
