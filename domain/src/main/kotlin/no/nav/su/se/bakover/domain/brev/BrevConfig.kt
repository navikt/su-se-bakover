package no.nav.su.se.bakover.domain.brev

sealed class BrevConfig {

    object Vedtak : BrevConfig()

    data class Fritekst(
        private val fritekst: String
    ) : BrevConfig() {
        fun getFritekst() = fritekst
    }
}
