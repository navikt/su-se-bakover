package no.nav.su.se.bakover.domain.revurdering.vilkår.opphold

import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert {
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out OpprettetRevurdering>,
    ) : KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert

    data object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert
}
