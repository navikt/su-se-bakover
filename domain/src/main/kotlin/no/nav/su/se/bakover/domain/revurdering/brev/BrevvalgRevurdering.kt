package no.nav.su.se.bakover.domain.revurdering.brev

import arrow.core.Either
import arrow.core.left
import arrow.core.right

sealed interface BrevvalgRevurdering {

    fun skalSendeBrev(): Either<Unit, Valgt.SendBrev> {
        return when (this) {
            IkkeValgt -> Unit.left()
            is Valgt.IkkeSendBrev -> Unit.left()
            is Valgt.SendBrev -> this.right()
        }
    }

    sealed interface Valgt : BrevvalgRevurdering {
        data class SendBrev(
            val fritekst: String?,
            val begrunnelse: String?,
            val bestemtAv: BestemtAv,
        ) : Valgt

        data class IkkeSendBrev(
            val begrunnelse: String?,
            val bestemtAv: BestemtAv,
        ) : Valgt
    }

    object IkkeValgt : BrevvalgRevurdering

    sealed class BestemtAv {
        object Systembruker : BestemtAv() {
            override fun toString(): String {
                return "srvsupstonad"
            }
        }
        data class Behandler(val ident: String) : BestemtAv() {
            override fun toString(): String {
                return ident
            }
        }
    }
}
