package no.nav.su.se.bakover.domain.revurdering

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.fixedClock
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.startOfMonth
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørsperiodeForUtbetalinger
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nåtidForSimuleringStub
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class OpphørsperiodeForUtbetalingerTest {

    @Test
    fun `opphørsdato er lik revurdering fra og med hvis ingen avkortingsvarsel`() {
        val revurderingsperiode =
            Periode.create(LocalDate.now(nåtidForSimuleringStub).startOfMonth(), 31.desember(2021))
        val simulert = simulertRevurdering(
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag(periode = revurderingsperiode)),
        ).second as SimulertRevurdering.Opphørt

        OpphørsperiodeForUtbetalinger(simulert).getOrFail().value shouldBe revurderingsperiode
        simulert.avkorting shouldBe beOfType<AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående>()
    }

    @Test
    fun `opphørsdato er lik første måned uten utbetalte beløp hvis feilutbetalinger kan avkortes`() {
        val tidligsteFraOgMedSomIkkeErUtbetalt = LocalDate.now(nåtidForSimuleringStub).startOfMonth()
        val simulert = simulertRevurdering(
            vilkårOverrides = listOf(utenlandsoppholdAvslag()),
            clock = TikkendeKlokke(1.august(2021).fixedClock()),
            utbetalingerKjørtTilOgMed = { tidligsteFraOgMedSomIkkeErUtbetalt },
        ).second as SimulertRevurdering.Opphørt
        OpphørsperiodeForUtbetalinger(simulert).getOrFail().value shouldBe Periode.create(
            tidligsteFraOgMedSomIkkeErUtbetalt,
            simulert.periode.tilOgMed,
        )
        simulert.avkorting shouldBe beOfType<AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel>()
    }

    @Test
    fun `Kan simulere simulere periode der alle månedene fører til avkorting`() {
        val revurderingsperiode = januar(2021)..mars(2021)
        val clock = TikkendeKlokke()
        simulertRevurdering(
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    periode = revurderingsperiode,
                    opprettet = Tidspunkt.now(clock),
                ),
            ),
            clock = clock,
            utbetalingerKjørtTilOgMed = { 1.juli(2021) },
        ).second.shouldBeInstanceOf<SimulertRevurdering.Opphørt>().also {
            OpphørsperiodeForUtbetalinger(it).shouldBeLeft()
        }
    }
}
