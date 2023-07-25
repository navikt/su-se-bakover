package no.nav.su.se.bakover.common.ident

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.su.se.bakover.common.suSeBakoverConsumerId

/**
 * TODO jah: Bør lage en Json-versjon, domenetyper skal ikke serialiseres/deserialiseres direkte.
 */
sealed class NavIdentBruker : Comparable<NavIdentBruker> {

    @get:JsonValue
    abstract val navIdent: String

    override fun toString(): String = navIdent

    override fun equals(other: Any?) = other is NavIdentBruker && navIdent == other.navIdent

    override fun compareTo(other: NavIdentBruker) = navIdent.compareTo(other.navIdent)

    override fun hashCode() = navIdent.hashCode()

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

        companion object {
            fun systembruker() = Saksbehandler(suSeBakoverConsumerId)
        }

        override fun toString(): String = navIdent
    }

    data class Veileder(override val navIdent: String) : NavIdentBruker() {
        init {
            validate()
        }

        override fun toString(): String = navIdent
    }

    data class Drift(override val navIdent: String) : NavIdentBruker() {
        init {
            validate()
        }

        override fun toString(): String = navIdent
    }
}
