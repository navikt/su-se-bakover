package no.nav.su.se.bakover.service.klage

import behandling.klage.domain.FormkravTilKlage
import behandling.klage.domain.FormkravTilKlage.Svarord
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import java.util.UUID

/**
 * I første omgang vi saksbehandler mulighet til å delvis oppdatere vilkårsvurderingene til en klage.
 */
data class VurderKlagevilkårCommand(
    val sakId: UUID,
    val klageId: KlageId,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val vedtakId: UUID?,
    val innenforFristen: Svarord?,
    val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
    val erUnderskrevet: Svarord?,
    val fremsattRettsligKlageinteresse: Svarord?,
) {
    val formkrav = FormkravTilKlage.create(
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        fremsattRettsligKlageinteresse = fremsattRettsligKlageinteresse,
    )
}
