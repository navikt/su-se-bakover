package no.nav.su.se.bakover.service.klage

import behandling.klage.domain.FormkravTilKlage
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
    val innenforFristen: FormkravTilKlage.Svarord?,
    val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
    val erUnderskrevet: FormkravTilKlage.Svarord?,
) {
    val vilkårsvurderinger = FormkravTilKlage.create(
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
    )
}
