package no.nav.su.se.bakover.domain.revurdering.brev

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID

sealed interface BrevvalgRevurdering {
    fun skalSendeBrev(): Either<Unit, Valgt.SendBrev> {
        return when (this) {
            IkkeValgt -> Unit.left()
            is Valgt.IkkeSendBrev -> Unit.left()
            is Valgt.SendBrev -> this.right()
        }
    }

    fun kanSendesTilAttestering(): Boolean = when (this) {
        is Valgt -> this.kanSendesTilAttestering()
        is IkkeValgt -> false
    }

    sealed interface Valgt : BrevvalgRevurdering {
        val bestemtAv: BestemtAv
        val begrunnelse: String?

        override fun kanSendesTilAttestering(): Boolean = when (this) {
            is SendBrev -> fritekst != null
            is IkkeSendBrev -> true
        }

        data class SendBrev(
            val fritekst: String?,
            override val begrunnelse: String?,
            override val bestemtAv: BestemtAv,
        ) : Valgt

        data class IkkeSendBrev(
            override val begrunnelse: String?,
            override val bestemtAv: BestemtAv,
        ) : Valgt
    }

    data object IkkeValgt : BrevvalgRevurdering

    sealed interface BestemtAv {
        data object Systembruker : BestemtAv {
            override fun toString(): String {
                return SU_SE_BAKOVER_CONSUMER_ID
            }
        }
        data class Behandler(val ident: String) : BestemtAv {
            override fun toString(): String {
                return ident
            }
        }
    }
}
