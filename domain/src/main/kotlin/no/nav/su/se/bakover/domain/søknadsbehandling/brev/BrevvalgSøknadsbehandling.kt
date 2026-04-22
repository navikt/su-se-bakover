package no.nav.su.se.bakover.domain.søknadsbehandling.brev
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID

sealed interface BrevvalgSøknadsbehandling {
    fun skalSendeBrev(): Either<Unit, Valgt.SendBrev> {
        return when (this) {
            IkkeValgt -> Unit.left()
            is Valgt.IkkeSendBrev -> Unit.left()
            is Valgt.SendBrev -> this.right()
        }
    }

    sealed interface Valgt : BrevvalgSøknadsbehandling {
        val bestemtAv: BestemtAv

        data class SendBrev(
            override val bestemtAv: BestemtAv,
        ) : Valgt

        data class IkkeSendBrev(
            override val bestemtAv: BestemtAv,
        ) : Valgt
    }

    data object IkkeValgt : BrevvalgSøknadsbehandling

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
