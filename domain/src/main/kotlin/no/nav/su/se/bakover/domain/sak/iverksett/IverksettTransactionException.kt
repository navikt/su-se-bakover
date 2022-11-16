package no.nav.su.se.bakover.domain.sak.iverksett
data class IverksettTransactionException(
    override val message: String,
    val feil: KunneIkkeFerdigstilleIverksettelsestransaksjon,
) : RuntimeException(message)
