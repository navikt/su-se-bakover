package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import java.util.UUID

/**
 * I første omgang vi saksbehandler mulighet til å delvis oppdatere vilkårsvurderingene til en klage.
 */
data class VurderKlagevilkårRequest(
    val klageId: UUID,
    private val saksbehandler: NavIdentBruker.Saksbehandler,
    private val vedtakId: UUID?,
    private val innenforFristen: VilkårsvurderingerTilKlage.Svarord?,
    private val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
    private val erUnderskrevet: VilkårsvurderingerTilKlage.Svarord?,
    private val begrunnelse: String?,
) {
    data class Domain(
        val klageId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val vilkårsvurderinger: VilkårsvurderingerTilKlage,
    )

    fun toDomain(): Either<KunneIkkeVilkårsvurdereKlage, Domain> {
        return Domain(
            klageId = klageId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = VilkårsvurderingerTilKlage.create(
                vedtakId = vedtakId,
                innenforFristen = innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet = erUnderskrevet,
                begrunnelse = begrunnelse,
            ),
        ).right()
    }
}
