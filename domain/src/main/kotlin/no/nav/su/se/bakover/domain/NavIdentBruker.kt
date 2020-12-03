package no.nav.su.se.bakover.domain

import com.fasterxml.jackson.annotation.JsonValue

sealed class NavIdentBruker {
    @get:JsonValue
    abstract val navIdent: String

    data class Attestant(override val navIdent: String) : NavIdentBruker() {
        override fun toString(): String = navIdent
    }

    data class Saksbehandler(override val navIdent: String) : NavIdentBruker() {
        override fun toString(): String = navIdent
    }
}
