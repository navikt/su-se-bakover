package no.nav.su.se.bakover.database.brev

import no.nav.su.se.bakover.common.suSeBakoverConsumerId
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering

internal data class BrevvalgRevurderingDbJson(
    val type: BrevvalgRevurderingDbType,
    val fritekst: String?,
    val begrunnelse: String?,
    val bestemtav: String?,
)
internal enum class BrevvalgRevurderingDbType {
    SEND_BREV,
    IKKE_SEND_BREV,
    IKKE_VALGT,
}

private val SYSTEMBRUKER_DB = suSeBakoverConsumerId

internal fun BrevvalgRevurdering.toDb(): BrevvalgRevurderingDbJson {
    return when (this) {
        BrevvalgRevurdering.IkkeValgt -> {
            BrevvalgRevurderingDbJson(
                type = BrevvalgRevurderingDbType.IKKE_VALGT,
                fritekst = null,
                begrunnelse = null,
                bestemtav = null,
            )
        }
        is BrevvalgRevurdering.Valgt.IkkeSendBrev -> {
            BrevvalgRevurderingDbJson(
                type = BrevvalgRevurderingDbType.IKKE_SEND_BREV,
                fritekst = null,
                begrunnelse = begrunnelse,
                bestemtav = when (val verdi = bestemtAv) {
                    is BrevvalgRevurdering.BestemtAv.Behandler -> verdi.ident
                    BrevvalgRevurdering.BestemtAv.Systembruker -> SYSTEMBRUKER_DB
                },
            )
        }
        is BrevvalgRevurdering.Valgt.SendBrev -> {
            BrevvalgRevurderingDbJson(
                type = BrevvalgRevurderingDbType.SEND_BREV,
                fritekst = fritekst,
                begrunnelse = begrunnelse,
                bestemtav = when (val verdi = bestemtAv) {
                    is BrevvalgRevurdering.BestemtAv.Behandler -> verdi.ident
                    BrevvalgRevurdering.BestemtAv.Systembruker -> SYSTEMBRUKER_DB
                },
            )
        }
    }
}

internal fun BrevvalgRevurderingDbJson.toDomain(): BrevvalgRevurdering {
    fun bestemtAv(string: String): BrevvalgRevurdering.BestemtAv {
        return if (string == SYSTEMBRUKER_DB) {
            BrevvalgRevurdering.BestemtAv.Systembruker
        } else {
            BrevvalgRevurdering.BestemtAv.Behandler(string)
        }
    }
    return when (this.type) {
        BrevvalgRevurderingDbType.IKKE_VALGT -> {
            BrevvalgRevurdering.IkkeValgt
        }
        BrevvalgRevurderingDbType.IKKE_SEND_BREV -> {
            BrevvalgRevurdering.Valgt.IkkeSendBrev(
                begrunnelse = begrunnelse,
                bestemtAv = bestemtAv(bestemtav!!),
            )
        }
        BrevvalgRevurderingDbType.SEND_BREV -> {
            BrevvalgRevurdering.Valgt.SendBrev(
                fritekst = fritekst,
                begrunnelse = begrunnelse,
                bestemtAv = bestemtAv(bestemtav!!),
            )
        }
    }
}
