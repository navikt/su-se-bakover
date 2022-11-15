package no.nav.su.se.bakover.database.brev

import no.nav.su.se.bakover.domain.revurdering.BrevvalgRevurdering

data class BrevvalgVedtaksbrevDbJson(
    val type: BrevvalgVedtaksbrevDbType,
    val fritekst: String?,
    val begrunnelse: String?,
    val bestemtav: String?,
)
enum class BrevvalgVedtaksbrevDbType {
    SEND_BREV,
    IKKE_SEND_BREV,
    IKKE_VALGT,
    ;
}

fun BrevvalgRevurdering.toDb(): BrevvalgVedtaksbrevDbJson {
    return when (this) {
        BrevvalgRevurdering.IkkeValgt -> {
            BrevvalgVedtaksbrevDbJson(
                type = BrevvalgVedtaksbrevDbType.IKKE_VALGT,
                fritekst = null,
                begrunnelse = null,
                bestemtav = null,
            )
        }
        is BrevvalgRevurdering.Valgt.IkkeSendBrev -> {
            BrevvalgVedtaksbrevDbJson(
                type = BrevvalgVedtaksbrevDbType.IKKE_SEND_BREV,
                fritekst = null,
                begrunnelse = begrunnelse,
                bestemtav = bestemtAv.toString(),
            )
        }
        is BrevvalgRevurdering.Valgt.SendBrev -> {
            BrevvalgVedtaksbrevDbJson(
                type = BrevvalgVedtaksbrevDbType.SEND_BREV,
                fritekst = fritekst,
                begrunnelse = begrunnelse,
                bestemtav = bestemtAv.toString(),
            )
        }
    }
}

fun BrevvalgVedtaksbrevDbJson.toDomain(): BrevvalgRevurdering {
    fun bestemtAv(string: String): BrevvalgRevurdering.BestemtAv {
        return if (string == BrevvalgRevurdering.BestemtAv.Systembruker.toString()) {
            BrevvalgRevurdering.BestemtAv.Systembruker
        } else {
            BrevvalgRevurdering.BestemtAv.Behandler(string)
        }
    }
    return when (this.type) {
        BrevvalgVedtaksbrevDbType.IKKE_VALGT -> {
            BrevvalgRevurdering.IkkeValgt
        }
        BrevvalgVedtaksbrevDbType.IKKE_SEND_BREV -> {
            BrevvalgRevurdering.Valgt.IkkeSendBrev(
                begrunnelse = begrunnelse,
                bestemtAv = bestemtAv(bestemtav!!),
            )
        }
        BrevvalgVedtaksbrevDbType.SEND_BREV -> {
            BrevvalgRevurdering.Valgt.SendBrev(
                fritekst = fritekst,
                begrunnelse = begrunnelse,
                bestemtAv = bestemtAv(bestemtav!!),
            )
        }
    }
}
