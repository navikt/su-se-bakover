package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import java.util.UUID

/**
 * I første omgang vi saksbehandler mulighet til å delvis oppdatere vilkårsvurderingene til en klage.
 */
data class VurderKlagevilkårRequest(
    val klageId: String,
    private val navIdent: String,
    private val vedtakId: String?,
    private val innenforFristen: Boolean?,
    private val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
    private val erUnderskrevet: Boolean?,
    private val begrunnelse: String?,
) {
    data class Domain(
        val klageId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val vilkårsvurderinger: VilkårsvurderingerTilKlage,
    )

    fun toDomain(): Either<KunneIkkeVilkårsvurdereKlage, Domain> {
        val saksbehandler = NavIdentBruker.Saksbehandler(navIdent = navIdent)
        val klageId = Either.catch { UUID.fromString(klageId) }
            .getOrElse { return KunneIkkeVilkårsvurdereKlage.FantIkkeKlage.left() }
        val vedtakId = vedtakId?.let {
            Either.catch { UUID.fromString(it) }
                .getOrElse { return KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak.left() }
        }
        val vilkårsvurderinger = if (listOf<Any?>(
                vedtakId,
                innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet,
                begrunnelse,
            ).any { it == null }
        ) {
            VilkårsvurderingerTilKlage.Påbegynt(
                vedtakId = vedtakId,
                innenforFristen = innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet = erUnderskrevet,
                begrunnelse = begrunnelse,
            )
        } else {
            VilkårsvurderingerTilKlage.Ferdig(
                vedtakId = vedtakId!!,
                innenforFristen = innenforFristen!!,
                klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket!!,
                erUnderskrevet = erUnderskrevet!!,
                begrunnelse = begrunnelse!!,
            )
        }
        return Domain(
            klageId = klageId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = vilkårsvurderinger,
        ).right()
    }
}
