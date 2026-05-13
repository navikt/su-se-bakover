package no.nav.su.se.bakover.database.brev

import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgBehandling

internal data class BrevvalgBehandlingDbJson(
    val type: BrevvalgBehandlingDbType,
    val begrunnelse: String?,
    val bestemtav: String?,
)
internal enum class BrevvalgBehandlingDbType {
    SEND_BREV,
    IKKE_SEND_BREV,
    IKKE_VALGT,
}

private val SYSTEMBRUKER_DB = SU_SE_BAKOVER_CONSUMER_ID

internal fun BrevvalgBehandling.toDb(): BrevvalgBehandlingDbJson {
    return when (this) {
        BrevvalgBehandling.IkkeValgt -> {
            BrevvalgBehandlingDbJson(
                type = BrevvalgBehandlingDbType.IKKE_VALGT,
                begrunnelse = null,
                bestemtav = null,
            )
        }
        is BrevvalgBehandling.Valgt.IkkeSendBrev -> {
            BrevvalgBehandlingDbJson(
                type = BrevvalgBehandlingDbType.IKKE_SEND_BREV,
                begrunnelse = begrunnelse,
                bestemtav = when (val verdi = bestemtAv) {
                    is BrevvalgBehandling.BestemtAv.Behandler -> verdi.ident
                    BrevvalgBehandling.BestemtAv.Systembruker -> SYSTEMBRUKER_DB
                },
            )
        }
        is BrevvalgBehandling.Valgt.SendBrev -> {
            BrevvalgBehandlingDbJson(
                type = BrevvalgBehandlingDbType.SEND_BREV,
                begrunnelse = begrunnelse,
                bestemtav = when (val verdi = bestemtAv) {
                    is BrevvalgBehandling.BestemtAv.Behandler -> verdi.ident
                    BrevvalgBehandling.BestemtAv.Systembruker -> SYSTEMBRUKER_DB
                },
            )
        }
    }
}

internal fun BrevvalgBehandlingDbJson.toDomain(): BrevvalgBehandling {
    fun bestemtAv(string: String): BrevvalgBehandling.BestemtAv {
        return if (string == SYSTEMBRUKER_DB) {
            BrevvalgBehandling.BestemtAv.Systembruker
        } else {
            BrevvalgBehandling.BestemtAv.Behandler(string)
        }
    }
    return when (this.type) {
        BrevvalgBehandlingDbType.IKKE_VALGT -> {
            BrevvalgBehandling.IkkeValgt
        }
        BrevvalgBehandlingDbType.IKKE_SEND_BREV -> {
            BrevvalgBehandling.Valgt.IkkeSendBrev(
                begrunnelse = begrunnelse,
                bestemtAv = bestemtAv(bestemtav!!),
            )
        }
        BrevvalgBehandlingDbType.SEND_BREV -> {
            BrevvalgBehandling.Valgt.SendBrev(
                begrunnelse = begrunnelse,
                bestemtAv = bestemtAv(bestemtav!!),
            )
        }
    }
}
