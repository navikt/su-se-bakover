package no.nav.su.se.bakover.domain.revurdering

sealed interface BrevvalgRevurdering {

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
                return "System"
            }
        }
        data class Behandler(val ident: String) : BestemtAv() {
            override fun toString(): String {
                return ident
            }
        }
    }
}
