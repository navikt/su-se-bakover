package no.nav.su.se.bakover.domain.revurdering.brev

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.RevurderingId

data class LeggTilBrevvalgRequest(
    val revurderingId: RevurderingId,
    val valg: Valg,
    val fritekst: String?,
    val begrunnelse: String?,
    val saksbehandler: NavIdentBruker.Saksbehandler,
) {
    enum class Valg {
        SEND,
        IKKE_SEND,
    }

    fun toDomain(): BrevvalgRevurdering.Valgt {
        return when (valg) {
            Valg.SEND -> {
                BrevvalgRevurdering.Valgt.SendBrev(
                    fritekst = fritekst,
                    begrunnelse = begrunnelse,
                    bestemtAv = BrevvalgRevurdering.BestemtAv.Behandler(saksbehandler.navIdent),
                )
            }

            Valg.IKKE_SEND -> {
                BrevvalgRevurdering.Valgt.IkkeSendBrev(
                    begrunnelse = begrunnelse,
                    bestemtAv = BrevvalgRevurdering.BestemtAv.Behandler(saksbehandler.navIdent),
                )
            }
        }
    }
}
