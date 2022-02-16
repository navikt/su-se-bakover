package no.nav.su.se.bakover.domain.bruker

import com.fasterxml.jackson.annotation.JsonValue

sealed class NavIdentBruker {
    @get:JsonValue
    abstract val navIdent: String

    protected fun validate() {
        if (navIdent.isBlank()) {
            throw IllegalArgumentException("navIdent kan ikke være en blank streng.")
        }
    }

    data class Attestant(override val navIdent: String) : NavIdentBruker() {
        init {
            validate()
        }

        override fun toString(): String = navIdent
    }

    data class Saksbehandler(override val navIdent: String) : NavIdentBruker() {
        init {
            validate()
        }

        override fun toString(): String = navIdent
    }
}
