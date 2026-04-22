package no.nav.su.se.bakover.database.brev

import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.BrevvalgSøknadsbehandling

internal data class BrevvalgSøknadsbehandlingDbJson(
    val type: BrevvalgSøknadsbehandlingDbType,
    val bestemtav: String?,
)
internal enum class BrevvalgSøknadsbehandlingDbType {
    SEND_BREV,
    IKKE_SEND_BREV,
    IKKE_VALGT,
}

private val SYSTEMBRUKER_DB = SU_SE_BAKOVER_CONSUMER_ID

internal fun BrevvalgSøknadsbehandling.toDb(): BrevvalgSøknadsbehandlingDbJson {
    return when (this) {
        BrevvalgSøknadsbehandling.IkkeValgt -> {
            BrevvalgSøknadsbehandlingDbJson(
                type = BrevvalgSøknadsbehandlingDbType.IKKE_VALGT,
                bestemtav = null,
            )
        }
        is BrevvalgSøknadsbehandling.Valgt.IkkeSendBrev -> {
            BrevvalgSøknadsbehandlingDbJson(
                type = BrevvalgSøknadsbehandlingDbType.IKKE_SEND_BREV,
                bestemtav = when (val verdi = bestemtAv) {
                    is BrevvalgSøknadsbehandling.BestemtAv.Behandler -> verdi.ident
                    BrevvalgSøknadsbehandling.BestemtAv.Systembruker -> SYSTEMBRUKER_DB
                },
            )
        }
        is BrevvalgSøknadsbehandling.Valgt.SendBrev -> {
            BrevvalgSøknadsbehandlingDbJson(
                type = BrevvalgSøknadsbehandlingDbType.SEND_BREV,
                bestemtav = when (val verdi = bestemtAv) {
                    is BrevvalgSøknadsbehandling.BestemtAv.Behandler -> verdi.ident
                    BrevvalgSøknadsbehandling.BestemtAv.Systembruker -> SYSTEMBRUKER_DB
                },
            )
        }
    }
}

internal fun BrevvalgSøknadsbehandlingDbJson.toDomain(): BrevvalgSøknadsbehandling {
    fun bestemtAv(string: String): BrevvalgSøknadsbehandling.BestemtAv {
        return if (string == SYSTEMBRUKER_DB) {
            BrevvalgSøknadsbehandling.BestemtAv.Systembruker
        } else {
            BrevvalgSøknadsbehandling.BestemtAv.Behandler(string)
        }
    }
    return when (this.type) {
        BrevvalgSøknadsbehandlingDbType.IKKE_VALGT -> {
            BrevvalgSøknadsbehandling.IkkeValgt
        }
        BrevvalgSøknadsbehandlingDbType.IKKE_SEND_BREV -> {
            BrevvalgSøknadsbehandling.Valgt.IkkeSendBrev(
                bestemtAv = bestemtAv(bestemtav!!),
            )
        }
        BrevvalgSøknadsbehandlingDbType.SEND_BREV -> {
            BrevvalgSøknadsbehandling.Valgt.SendBrev(
                bestemtAv = bestemtAv(bestemtav!!),
            )
        }
    }
}
