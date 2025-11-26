package no.nav.su.se.bakover.common.brukerrolle

enum class Brukerrolle(val type: String) {
    Attestant("ATTESTANT"),
    Saksbehandler("SAKSBEHANDLER"),
    Veileder("VEILEDER"),
    Drift("DRIFT"),
}
