package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgBehandling

fun sendBrev(
    begrunnelse: String? = null,
    bestemtAv: BrevvalgBehandling.BestemtAv = BrevvalgBehandling.BestemtAv.Behandler(saksbehandler.navIdent),
): BrevvalgBehandling.Valgt.SendBrev {
    return BrevvalgBehandling.Valgt.SendBrev(
        begrunnelse = begrunnelse,
        bestemtAv = bestemtAv,
    )
}

fun ikkeSendBrev(
    begrunnelse: String? = null,
    bestemtAv: BrevvalgBehandling.BestemtAv = BrevvalgBehandling.BestemtAv.Behandler(saksbehandler.navIdent),
): BrevvalgBehandling.Valgt.IkkeSendBrev {
    return BrevvalgBehandling.Valgt.IkkeSendBrev(
        begrunnelse = begrunnelse,
        bestemtAv = bestemtAv,
    )
}
