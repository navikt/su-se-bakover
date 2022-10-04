package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import org.junit.jupiter.api.Test

internal class RevurderingTilAttesteringTest {
    @Test
    fun `sjekker at attstant og saksbehandler er ulik ved iverksettelse`() {
        tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().let { (_, revurdering) ->
            revurdering.tilIverksatt(
                attestant = NavIdentBruker.Attestant(revurdering.saksbehandler.navIdent),
                clock = fixedClock,
                hentOpprinneligAvkorting = { null },
            ) shouldBe RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }
    }

    // TODO flere tester på tiliverksatt avkorting (husk søknadsbehandling også)
}
