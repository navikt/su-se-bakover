package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.LocalDate
import java.util.UUID

internal class RevurderingTilAttesteringTest {
    @Test
    fun `sjekker at attstant og saksbehandler er ulik ved iverksettelse`() {
        val utbetalingsfunksjon =
            mock<(uuid: UUID, attestant: NavIdentBruker.Attestant, localDate: LocalDate, simulering: Simulering) -> Either<RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale, UUID30>>()

        tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().let { (_, revurdering) ->
            revurdering.tilIverksatt(
                attestant = NavIdentBruker.Attestant(revurdering.saksbehandler.navIdent),
                clock = fixedClock,
                utbetal = utbetalingsfunksjon,
            ) shouldBe RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

            verify(utbetalingsfunksjon, never())
        }
    }

    @Test
    fun `kaster exception dersom avkortingsvarsel er i ugyldig tilstand`() {
        val utbetalingsfunksjon =
            mock<(uuid: UUID, attestant: NavIdentBruker.Attestant, localDate: LocalDate, simulering: Simulering) -> Either<RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale, UUID30>>()

        assertThrows<IllegalStateException> {
            tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().let { (_, revurdering) ->
                revurdering.copy(
                    avkortingsvarsel = mock<Avkortingsvarsel.Utenlandsopphold.SkalAvkortes>(),
                ).tilIverksatt(
                    attestant = attestant,
                    clock = fixedClock,
                    utbetal = utbetalingsfunksjon,
                )
            }
        }

        assertThrows<IllegalStateException> {
            tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().let { (_, revurdering) ->
                revurdering.copy(
                    avkortingsvarsel = mock<Avkortingsvarsel.Utenlandsopphold.Avkortet>(),
                ).tilIverksatt(
                    attestant = attestant,
                    clock = fixedClock,
                    utbetal = utbetalingsfunksjon,
                )
            }
        }

        verify(utbetalingsfunksjon, never())
    }
}
