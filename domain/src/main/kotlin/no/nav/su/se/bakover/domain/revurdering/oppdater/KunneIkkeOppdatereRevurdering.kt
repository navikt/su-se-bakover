package no.nav.su.se.bakover.domain.revurdering.oppdater

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeOppdatereRevurdering {
    object FantIkkeSak : KunneIkkeOppdatereRevurdering
    object FantIkkeRevurdering : KunneIkkeOppdatereRevurdering

    data class UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(
        val avkortingsvarselperiode: Periode,
    ) : KunneIkkeOppdatereRevurdering

    data class GjeldendeVedtaksdataKanIkkeRevurderes(
        val underliggende: Sak.GjeldendeVedtaksdataErUgyldigForRevurdering,
    ) : KunneIkkeOppdatereRevurdering

    data class OpphørteVilkårMåRevurderes(val underliggende: Sak.OpphørtVilkårMåRevurderes) : KunneIkkeOppdatereRevurdering
    object MåVelgeInformasjonSomSkalRevurderes : KunneIkkeOppdatereRevurdering
    object UgyldigÅrsak : KunneIkkeOppdatereRevurdering
    object UgyldigBegrunnelse : KunneIkkeOppdatereRevurdering
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeOppdatereRevurdering

    object KanIkkeEndreÅrsakTilReguleringVedForhåndsvarsletRevurdering : KunneIkkeOppdatereRevurdering
}
