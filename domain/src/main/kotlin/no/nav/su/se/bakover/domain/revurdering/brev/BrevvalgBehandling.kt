package no.nav.su.se.bakover.domain.revurdering.brev

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID

sealed interface BrevvalgBehandling {
    fun skalSendeBrev(): Either<Unit, Valgt.SendBrev> {
        return when (this) {
            IkkeValgt -> Unit.left()
            is Valgt.IkkeSendBrev -> Unit.left()
            is Valgt.SendBrev -> this.right()
        }
    }

    sealed interface Valgt : BrevvalgBehandling {
        val bestemtAv: BestemtAv

        @Deprecated("Kun for historisk visning. Nye begrunnelser legges i behandlingsnotat.")
        val begrunnelse: String?

        data class SendBrev private constructor(
            override val bestemtAv: BestemtAv,

            @Deprecated("Kun for historisk visning. Nye begrunnelser legges i behandlingsnotat.")
            override val begrunnelse: String? = null,
        ) : Valgt {

            @Suppress("deprecation")
            fun historiskBegrunnelse() = begrunnelse

            companion object {

                fun opprett(bestemtAv: BestemtAv) = SendBrev(bestemtAv)

                fun fraLagret(
                    bestemtAv: BestemtAv,
                    historiskBegrunnelse: String?,
                ) = SendBrev(bestemtAv, historiskBegrunnelse)
            }
        }

        data class IkkeSendBrev private constructor(
            override val bestemtAv: BestemtAv,

            @Deprecated("Kun for historisk visning. Nye begrunnelser legges i behandlingsnotat.")
            override val begrunnelse: String? = null,
        ) : Valgt {

            @Suppress("deprecation")
            fun historiskBegrunnelse() = begrunnelse

            companion object {

                fun opprett(bestemtAv: BestemtAv) = IkkeSendBrev(bestemtAv)

                fun fraLagret(
                    bestemtAv: BestemtAv,
                    historiskBegrunnelse: String?,
                ) = IkkeSendBrev(bestemtAv, historiskBegrunnelse)
            }
        }
    }

    data object IkkeValgt : BrevvalgBehandling

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
