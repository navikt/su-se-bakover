package no.nav.su.se.bakover.domain.revurdering.stans

data class StansAvYtelseTransactionException(
    override val message: String,
    val feil: KunneIkkeStanseYtelse,
) : RuntimeException(message) {
    companion object {
        fun KunneIkkeStanseYtelse.exception(): StansAvYtelseTransactionException {
            return when (this) {
                KunneIkkeStanseYtelse.FantIkkeSak -> {
                    StansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                KunneIkkeStanseYtelse.FantIkkeRevurdering -> {
                    StansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                KunneIkkeStanseYtelse.FinnesÅpenStansbehandling -> {
                    StansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering -> {
                    StansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                is KunneIkkeStanseYtelse.SimuleringAvStansFeilet -> {
                    StansAvYtelseTransactionException(this.feil::class.java.toString(), this)
                }
                is KunneIkkeStanseYtelse.UgyldigTypeForOppdatering -> {
                    StansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                is KunneIkkeStanseYtelse.UkjentFeil -> {
                    StansAvYtelseTransactionException(this.msg, this)
                }
            }
        }
    }
}
