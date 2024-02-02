package no.nav.su.se.bakover.domain.brev

sealed interface BrevConfig {
    fun getFritekst(): String?

    data class Vedtak(
        private val fritekst: String?,
    ) : BrevConfig {
        override fun getFritekst() = fritekst
    }

    data class Fritekst(
        private val fritekst: String,
    ) : BrevConfig {
        override fun getFritekst() = fritekst
    }
}
