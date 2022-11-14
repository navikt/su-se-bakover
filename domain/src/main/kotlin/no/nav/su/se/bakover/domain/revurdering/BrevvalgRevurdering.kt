package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right

sealed interface BrevvalgRevurdering {

    fun skalSendeBrev(): Either<Unit, SendBrev> {
        return when (this) {
            is IkkeSendBrev -> Unit.left()
            IkkeValgt -> Unit.left()
            is SendBrev -> this.right()
        }
    }

    data class SendBrev(
        val fritekst: String?,
        val begrunnelse: String?,
        val bestemtAv: BestemtAv,
    ) : BrevvalgRevurdering

    data class IkkeSendBrev(
        val begrunnelse: String?,
        val bestemtAv: BestemtAv,
    ) : BrevvalgRevurdering

    object IkkeValgt : BrevvalgRevurdering

    sealed class BestemtAv {
        object System : BestemtAv() {
            override fun toString(): String {
                return "SYSTEM"
            }
        }
        data class Behandler(val ident: String) : BestemtAv() {
            override fun toString(): String {
                return ident
            }
        }
    }
}
