package no.nav.su.se.bakover.domain.revurdering.oppdater

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeOppdatereRevurdering {

    data class GjeldendeVedtaksdataKanIkkeRevurderes(
        val underliggende: Sak.GjeldendeVedtaksdataErUgyldigForRevurdering,
    ) : KunneIkkeOppdatereRevurdering

    data class OpphørteVilkårMåRevurderes(val underliggende: Sak.OpphørtVilkårMåRevurderes) : KunneIkkeOppdatereRevurdering
    data object MåVelgeInformasjonSomSkalRevurderes : KunneIkkeOppdatereRevurdering
    data object UgyldigÅrsak : KunneIkkeOppdatereRevurdering
    data object UgyldigBegrunnelse : KunneIkkeOppdatereRevurdering
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeOppdatereRevurdering
}
