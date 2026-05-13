package no.nav.su.se.bakover.domain.revurdering.brev

import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.ident.NavIdentBruker

data class LeggTilBrevvalgRequest(
    val behandlingsId: BehandlingsId,
    val valg: Valg,
    val begrunnelse: String?,
    val saksbehandler: NavIdentBruker.Saksbehandler,
) {
    enum class Valg {
        SEND,
        IKKE_SEND,
    }

    fun toDomain(): BrevvalgBehandling.Valgt {
        return when (valg) {
            Valg.SEND -> {
                BrevvalgBehandling.Valgt.SendBrev(
                    begrunnelse = begrunnelse,
                    bestemtAv = BrevvalgBehandling.BestemtAv.Behandler(saksbehandler.navIdent),
                )
            }

            Valg.IKKE_SEND -> {
                BrevvalgBehandling.Valgt.IkkeSendBrev(
                    begrunnelse = begrunnelse,
                    bestemtAv = BrevvalgBehandling.BestemtAv.Behandler(saksbehandler.navIdent),
                )
            }
        }
    }
}
