package no.nav.su.se.bakover.domain.revurdering.iverksett
data class IverksettTransactionException(
    override val message: String,
    val feil: KunneIkkeFerdigstilleIverksettelsestransaksjon,
) : RuntimeException(message)
