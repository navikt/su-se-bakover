package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering

fun sendBrev(
    begrunnelse: String? = null,
    bestemtAv: BrevvalgRevurdering.BestemtAv = BrevvalgRevurdering.BestemtAv.Behandler(saksbehandler.navIdent),
): BrevvalgRevurdering.Valgt.SendBrev {
    return BrevvalgRevurdering.Valgt.SendBrev(
        begrunnelse = begrunnelse,
        bestemtAv = bestemtAv,
    )
}

fun ikkeSendBrev(
    begrunnelse: String? = null,
    bestemtAv: BrevvalgRevurdering.BestemtAv = BrevvalgRevurdering.BestemtAv.Behandler(saksbehandler.navIdent),
): BrevvalgRevurdering.Valgt.IkkeSendBrev {
    return BrevvalgRevurdering.Valgt.IkkeSendBrev(
        begrunnelse = begrunnelse,
        bestemtAv = bestemtAv,
    )
}
