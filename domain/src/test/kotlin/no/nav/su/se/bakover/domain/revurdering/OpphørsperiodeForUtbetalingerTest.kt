package no.nav.su.se.bakover.domain.revurdering

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.test.nåtidForSimuleringStub
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

        OpphørsperiodeForUtbetalinger(simulert).value shouldBe revurderingsperiode
        simulert.avkorting shouldBe beOfType<AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående>()
    }

    @Test
    fun `opphørsdato er lik første måned uten utbetalte beløp hvis feilutbetalinger kan avkortes`() {
        val tidligsteFraOgMedSomIkkeErUtbetalt = LocalDate.now(nåtidForSimuleringStub).startOfMonth()
        val simulert = simulertRevurdering(
            vilkårOverrides = listOf(utenlandsoppholdAvslag()),
        ).second as SimulertRevurdering.Opphørt
        OpphørsperiodeForUtbetalinger(simulert).value shouldBe Periode.create(
            tidligsteFraOgMedSomIkkeErUtbetalt,
            simulert.periode.tilOgMed,
        )
        simulert.avkorting shouldBe beOfType<AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel>()
    }

    @Test
    fun `kaster exception hvis opphørsdato for utbetalinger er utenfor revurderingsperioden`() {
        assertThrows<IllegalStateException> {
            val revurderingsperiode = Periode.create(1.januar(2021), 31.mars(2021))
            simulertRevurdering(
                revurderingsperiode = revurderingsperiode,
                vilkårOverrides = listOf(utenlandsoppholdAvslag(periode = revurderingsperiode)),
                clock = tikkendeFixedClock,
            ).second as SimulertRevurdering.Opphørt
        }.also {
            it.message shouldContain "Opphørsdato er utenfor revurderingsperioden"
        }
    }
}
