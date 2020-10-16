package no.nav.su.se.bakover.domain

sealed class NavIdentBruker {
    abstract val navIdent: String

    data class Attestant(override val navIdent: String) : NavIdentBruker()
    data class Saksbehandler(override val navIdent: String) : NavIdentBruker()
}
