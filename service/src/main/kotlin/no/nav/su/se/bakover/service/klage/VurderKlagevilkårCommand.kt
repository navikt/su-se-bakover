package no.nav.su.se.bakover.service.klage

import behandling.klage.domain.FormkravTilKlage
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import java.util.UUID

data class VurderKlagevilkårCommand(
    val sakId: UUID,
    val klageId: KlageId,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val vedtakId: UUID?,
    val innenforFristen: FormkravTilKlage.SvarMedBegrunnelse?,
    val klagesDetPåKonkreteElementerIVedtaket: FormkravTilKlage.BooleanMedBegrunnelse?,
    val erUnderskrevet: FormkravTilKlage.SvarMedBegrunnelse?,
    val fremsattRettsligKlageinteresse: FormkravTilKlage.SvarMedBegrunnelse?,
) {
    val formkrav = FormkravTilKlage.create(
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        fremsattRettsligKlageinteresse = fremsattRettsligKlageinteresse,
    )
}
