package no.nav.su.se.bakover.common

enum class Brukerrolle(val type: String) {
    Attestant("ATTESTANT"),
    Saksbehandler("SAKSBEHANDLER"),
    Veileder("VEILEDER"),
    Drift("DRIFT"),
}
