package no.nav.su.se.bakover.domain.bruker

enum class Brukerrolle(val type: String) {
    Attestant("ATTESTANT"),
    Saksbehandler("SAKSBEHANDLER"),
    Veileder("VEILEDER"),
    Drift("DRIFT");
}
