package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test

internal class VilkårsvurderingerRevurderingTilAttesteringTest {
    @Test
    fun `sjekker at attstant og saksbehandler er ulik ved iverksettelse`() {
        revurderingTilAttestering(vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()))
            .let { (sak, revurdering) -> sak to revurdering as RevurderingTilAttestering.Opphørt }.also { (_, revurdering) ->
                revurdering.tilIverksatt(
                    attestant = NavIdentBruker.Attestant(revurdering.saksbehandler.navIdent),
                    clock = fixedClock,
                ) shouldBe RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
    }
}
