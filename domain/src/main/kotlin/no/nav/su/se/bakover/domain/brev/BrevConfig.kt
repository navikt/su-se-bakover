package no.nav.su.se.bakover.domain.brev

abstract class BrevConfig {
    abstract fun getBrevtype(): BrevType

    data class BrevTypeConfig(
        private val brevType: BrevType
    ) : BrevConfig() {
        override fun getBrevtype(): BrevType = brevType
    }

    enum class BrevType {
        VEDTAK,
        FRITEKST
    }
}
