package no.nav.su.se.bakover.domain.revurdering.stans

data class IverksettStansAvYtelseTransactionException(
    override val message: String,
    val feil: KunneIkkeIverksetteStansYtelse,
) : RuntimeException(message) {
    companion object {
        fun KunneIkkeIverksetteStansYtelse.exception(): IverksettStansAvYtelseTransactionException {
            return when (this) {
                KunneIkkeIverksetteStansYtelse.FantIkkeRevurdering -> {
                    IverksettStansAvYtelseTransactionException(this::class.java.toString(), this)
                }

                is KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale -> {
                    IverksettStansAvYtelseTransactionException(this.feil::class.java.toString(), this)
                }

                KunneIkkeIverksetteStansYtelse.SimuleringIndikererFeilutbetaling -> {
                    IverksettStansAvYtelseTransactionException(this::class.java.toString(), this)
                }

                is KunneIkkeIverksetteStansYtelse.UgyldigTilstand -> {
                    IverksettStansAvYtelseTransactionException(this::class.java.toString(), this)
                }

                is KunneIkkeIverksetteStansYtelse.UkjentFeil -> {
                    IverksettStansAvYtelseTransactionException(this.msg, this)
                }
            }
        }
    }
}
