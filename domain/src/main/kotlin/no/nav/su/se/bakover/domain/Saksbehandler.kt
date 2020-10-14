package no.nav.su.se.bakover.domain

data class Saksbehandler(val navIdent: String) {
    override fun toString() = navIdent
}
